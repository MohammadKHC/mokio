/*
 * Copyright 2026 MohammedKHC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mohammedkhc.io

import com.sun.nio.file.ExtendedWatchEventModifier
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.*
import kotlin.concurrent.thread
import kotlin.io.path.isDirectory
import java.nio.file.Path as NioPath

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val service: WatchService = FileSystems.getDefault().newWatchService()
    private val events = events.map { it.watchServiceEvent }.toTypedArray()
    private val watchers = mutableMapOf<WatchKey, Watcher>()
    private var emulateRecursive = false
    private val highSensitivity =
        service.javaClass.canonicalName == "sun.nio.fs.PollingWatchService" &&
                highSensitivityModifier != null

    actual fun startWatching() {
        startWatching(path)
        thread(name = "FileWatcher", block = ::watch)
    }

    private fun startWatching(path: Path) = synchronized(watchers) {
        startWatcher(path)
        if (emulateRecursive) {
            FileSystem.SYSTEM
                .listRecursively(path)
                .filter(FileSystem.SYSTEM::isDirectory)
                .forEach(::startWatcher)
        }
    }

    actual fun stopWatching() {
        synchronized(watchers) {
            watchers.keys.forEach { it.cancel() }
            watchers.clear()
        }
        service.close()
    }

    private fun stopWatching(path: Path) = synchronized(watchers) {
        watchers.entries.removeAll {
            if (it.value.path.startsWith(path)) {
                it.key.cancel()
                true
            } else false
        }
    }

    private fun dispatchEvent(event: FileChangeEvent, path: Path) {
        if (emulateRecursive && path.toNioPath().isDirectory()) {
            when (event) {
                FileChangeEvent.Create -> startWatching(path)
                FileChangeEvent.Delete -> stopWatching(path)
                else -> {}
            }
        }
        onEvent(event, path)
    }

    private fun startWatcher(path: Path) {
        val nioPath = path.toNioPath()
        fun registerPath(fileTree: Boolean = false): WatchKey {
            val modifiers: Array<WatchEvent.Modifier> = when {
                highSensitivity && fileTree -> arrayOf(highSensitivityModifier!!, ExtendedWatchEventModifier.FILE_TREE)
                highSensitivity -> arrayOf(highSensitivityModifier!!)
                fileTree -> arrayOf(ExtendedWatchEventModifier.FILE_TREE)
                else -> emptyArray()
            }
            return nioPath.register(service, events, *modifiers)
        }

        val key = if (recursive && !emulateRecursive) {
            try {
                registerPath(fileTree = true)
            } catch (_: UnsupportedOperationException) {
                emulateRecursive = true
                registerPath()
            }
        } else registerPath()

        watchers[key] = Watcher(path, ::dispatchEvent)
    }

    private fun watch() {
        while (true) {
            val key = try {
                service.take()
            } catch (_: ClosedWatchServiceException) {
                break
            } catch (_: InterruptedException) {
                break
            }

            val watcher = synchronized(watchers) {
                watchers[key]
            } ?: continue

            val events = key.pollEvents()
            for (event in events) {
                val kind = event.kind()
                if (kind == StandardWatchEventKinds.OVERFLOW) continue
                val eventPath = event.context() as? NioPath ?: continue
                val path = watcher.path / eventPath.toOkioPath()

                watcher.onRawEvent(kind.changeEvent ?: continue, path)
            }

            if (!key.reset()) {
                key.cancel()
                synchronized(watchers) { watchers -= key }
            }
        }
    }

    private data class Watcher(
        val path: Path,
        val onRawEvent: (FileChangeEvent, resolvedPath: Path) -> Unit
    )

    private companion object {
        private val FileChangeEvent.watchServiceEvent
            get() = when (this) {
                FileChangeEvent.Create -> StandardWatchEventKinds.ENTRY_CREATE
                FileChangeEvent.Modify,
                FileChangeEvent.Attributes -> StandardWatchEventKinds.ENTRY_MODIFY

                FileChangeEvent.Delete -> StandardWatchEventKinds.ENTRY_DELETE
            }

        private val WatchEvent.Kind<*>.changeEvent
            get() = when (this) {
                StandardWatchEventKinds.ENTRY_CREATE -> FileChangeEvent.Create
                StandardWatchEventKinds.ENTRY_MODIFY -> FileChangeEvent.Modify
                StandardWatchEventKinds.ENTRY_DELETE -> FileChangeEvent.Delete
                else -> null
            }

        private val highSensitivityModifier by lazy {
            try {
                Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
                    .enumConstants[0] as? WatchEvent.Modifier
            } catch (_: Throwable) {
                null
            }
        }
    }
}
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

import android.os.Build
import android.os.FileObserver
import okio.Path
import okio.Path.Companion.toOkioPath

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val eventsMask = events.fold(0) { acc, it ->
        acc or it.inotifyEventMask
    }
    private val observers = mutableMapOf<Path, FileObserver>()

    actual fun startWatching() =
        startWatching(path, recursive)

    private fun startWatching(path: Path, recursive: Boolean) = synchronized(observers) {
        if (recursive) {
            path.toFile().walk().forEach {
                if (it.isDirectory) {
                    startObserver(it.toOkioPath())
                }
            }
        } else startObserver(path)
    }

    actual fun stopWatching() = synchronized(observers) {
        observers.values.forEach(FileObserver::stopWatching)
        observers.clear()
    }

    private fun stopWatching(path: Path) = synchronized(observers) {
        observers.entries.removeAll {
            if (it.key.startsWith(path)) {
                it.value.stopWatching()
                true
            } else false
        }
    }

    private fun dispatchEvent(eventMask: Int, path: Path) {
        if (eventMask and IN_IGNORED != 0) {
            stopWatching(path)
            return
        }
        val event = eventMask.changeEvent ?: return
        if (recursive && eventMask and IN_ISDIR != 0) {
            when (event) {
                FileChangeEvent.Create -> startWatching(path, true)
                FileChangeEvent.Delete -> stopWatching(path)
                else -> {}
            }
        }
        onEvent(event, path)
    }

    private fun startObserver(path: Path) {
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(path.toFile(), eventsMask) {
                override fun onEvent(event: Int, child: String?) =
                    dispatchEvent(event, child?.let(path::resolve) ?: path)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path.toString(), eventsMask) {
                override fun onEvent(event: Int, child: String?) =
                    dispatchEvent(event, child?.let(path::resolve) ?: path)
            }
        }
        observers[path] = observer
        observer.startWatching()
    }

    private companion object {
        const val IN_ISDIR = 0x40000000
        const val IN_IGNORED = 0x00008000

        val FileChangeEvent.inotifyEventMask
            get() = when (this) {
                FileChangeEvent.Create -> FileObserver.CREATE or FileObserver.MOVED_TO
                FileChangeEvent.Modify -> FileObserver.MODIFY
                FileChangeEvent.Attributes -> FileObserver.ATTRIB
                FileChangeEvent.Delete -> FileObserver.DELETE or FileObserver.MOVED_FROM
            }

        val Int.changeEvent
            get() = when {
                and(FileObserver.CREATE) != 0 || and(FileObserver.MOVED_TO) != 0 -> FileChangeEvent.Create
                and(FileObserver.MODIFY) != 0 -> FileChangeEvent.Modify
                and(FileObserver.ATTRIB) != 0 -> FileChangeEvent.Attributes
                and(FileObserver.DELETE) != 0 || and(FileObserver.MOVED_FROM) != 0 -> FileChangeEvent.Delete
                else -> null
            }
    }
}
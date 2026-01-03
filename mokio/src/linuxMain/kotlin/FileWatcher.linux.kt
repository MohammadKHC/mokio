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

import kotlinx.cinterop.*
import okio.FileSystem
import okio.Path
import platform.linux.*
import platform.posix.*

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val inotifyFd = inotify_init1(IN_CLOEXEC)
        .checkNotNegativeOne()
    private val eventsMask = events.fold(0) { acc, it ->
        acc or it.inotifyEventMask
    }

    private val watchers = mutableMapOf<Int, Watcher>()
    private val mutex = Mutex()

    actual fun startWatching() {
        startWatching(path, recursive)
        thread("FileWatcher", ::watch)
    }

    private fun startWatching(path: Path, recursive: Boolean) = mutex.withLock {
        startWatcher(path)
        if (recursive) {
            FileSystem.SYSTEM
                .listRecursively(path)
                .filter(FileSystem.SYSTEM::isDirectory)
                .forEach(::startWatcher)
        }
    }

    actual fun stopWatching() = mutex.withLock {
        watchers.keys.forEach {
            inotify_rm_watch(inotifyFd, it)
                .ensureSuccess()
        }
        watchers.clear()
        close(inotifyFd).ensureSuccess()
    }

    private fun stopWatching(path: Path) = mutex.withLock {
        watchers.entries.removeAll {
            if (it.value.path.startsWith(path)) {
                inotify_rm_watch(inotifyFd, it.key)
                    .ensureSuccess()
                true
            } else false
        }
    }

    private fun dispatchEvent(rawEvent: inotify_event, path: Path) {
        if (rawEvent.mask.toInt() and IN_IGNORED != 0) {
            stopWatching(path)
            return
        }
        val event = rawEvent.changeEvent ?: return
        if (recursive && rawEvent.mask.toInt() and IN_ISDIR != 0) {
            when (event) {
                FileChangeEvent.Create -> startWatching(path, true)
                FileChangeEvent.Delete -> stopWatching(path)
                else -> {}
            }
        }
        onEvent(event, path)
    }

    private fun startWatcher(path: Path) {
        val wd = inotify_add_watch(
            inotifyFd,
            path.toString(),
            eventsMask.toUInt()
        ).checkNotNegativeOne()
        watchers[wd] = Watcher(path, ::dispatchEvent)
    }

    private fun watch() = memScoped {
        val buffer = alloc(inotifyBufferSize, alignOf<inotify_event>())
        while (true) {
            val length = read(inotifyFd, buffer.reinterpret<ByteVar>().ptr, inotifyBufferSize.convert())
            if (length == -1L) {
                if (errno == EBADF || errno == EINTR)
                    break
                throw errnoToIOException()
            }
            if (length < sizeOf<inotify_event>()) {
                error("inotify got a short event.")
            }
            var offset = 0L
            while (offset < length) {
                val event = interpretPointed<inotify_event>(buffer.rawPtr + offset)
                mutex.withLock { watchers[event.wd] }?.apply {
                    val name = if (event.len > 0u) event.name.toKString() else null
                    onRawEvent(event, name?.let(path::resolve) ?: path)
                }
                offset += sizeOf<inotify_event>() + event.len.toLong()
            }
        }
    }

    private data class Watcher(
        val path: Path,
        val onRawEvent: (inotify_event, resolvedPath: Path) -> Unit
    )

    private companion object {
        val inotifyBufferSize = 5 * (sizeOf<inotify_event>() + NAME_MAX + 1)

        val FileChangeEvent.inotifyEventMask
            get() = when (this) {
                FileChangeEvent.Create -> IN_CREATE or IN_MOVED_TO
                FileChangeEvent.Modify -> IN_MODIFY
                FileChangeEvent.Attributes -> IN_ATTRIB
                FileChangeEvent.Delete -> IN_DELETE or IN_MOVED_FROM
            }

        val inotify_event.changeEvent
            get() = when {
                mask.toInt() and IN_CREATE != 0 || mask.toInt() and IN_MOVED_TO != 0 -> FileChangeEvent.Create
                mask.toInt() and IN_MODIFY != 0 -> FileChangeEvent.Modify
                mask.toInt() and IN_ATTRIB != 0 -> FileChangeEvent.Attributes
                mask.toInt() and IN_DELETE != 0 || mask.toInt() and IN_MOVED_FROM != 0 -> FileChangeEvent.Delete
                else -> null
            }
    }
}
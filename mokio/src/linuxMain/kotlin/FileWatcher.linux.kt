package com.mohammedkhc.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.alignOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.getRawPointer
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.interpretPointed
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import okio.Path
import platform.linux.IN_ATTRIB
import platform.linux.IN_CREATE
import platform.linux.IN_DELETE
import platform.linux.IN_MODIFY
import platform.linux.IN_MOVED_FROM
import platform.linux.IN_MOVED_TO
import platform.linux.inotify_add_watch
import platform.linux.inotify_event
import platform.linux.inotify_init
import platform.linux.inotify_rm_watch
import platform.posix.NAME_MAX
import platform.posix.read

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val eventsMask = events.fold(0) { acc, it ->
        acc or it.notifyEventMask
    }

    private var fd: Int? = null

    actual fun startWatching() = startWatching(this)
    actual fun stopWatching() = stopWatching(this)

    private fun dispatchEvent(event: Int, child: String?) {
        onEvent(
            event.changeEvent ?: return,
            child?.let(path::resolve) ?: path
        )
    }

    private val FileChangeEvent.notifyEventMask
        get() = when (this) {
            FileChangeEvent.Create -> IN_CREATE or IN_MOVED_TO
            FileChangeEvent.Modify -> IN_MODIFY or IN_ATTRIB
            FileChangeEvent.Delete -> IN_DELETE or IN_MOVED_FROM
        }

    private val Int.changeEvent
        get() = when (this and eventsMask) {
            IN_CREATE, IN_MOVED_TO -> FileChangeEvent.Create
            IN_MODIFY, IN_ATTRIB -> FileChangeEvent.Modify
            IN_DELETE, IN_MOVED_FROM -> FileChangeEvent.Delete
            else -> null
        }

    private companion object {
        var inotifyFd = -1
        var watchers = mutableMapOf<Int, FileWatcher>()
        val mutex = Mutex()
        var watcherThread: Thread? = null

        fun startWatching(watcher: FileWatcher) {
            val wfd = inotify_add_watch(
                inotifyFd,
                watcher.path.toString(),
                watcher.eventsMask.toUInt()
            ).checkNotNegativeOne()
            watcher.fd = wfd
            mutex.withLock { watchers[wfd] = watcher }
            if (watcherThread == null) {
                watchEvents()
            }
        }

        fun stopWatching(watcher: FileWatcher) {
            val wfd = watcher.fd ?: return
            inotify_rm_watch(inotifyFd, wfd)
                .ensureSuccess()
            watcher.fd = null
            mutex.withLock { watchers -= wfd }
        }

        fun watchEvents() {
            watcherThread = thread("FileWatcher") {
                memScoped {
                    val bufferSize = 512 // sizeOf<inotify_event>() + NAME_MAX + 1
                    val buffer = alloc(bufferSize, alignOf<inotify_event>())
                    while (watchers.isNotEmpty()) {
                        val length = read(inotifyFd, buffer.reinterpret<ByteVar>().ptr, bufferSize.convert())
                        if (length == -1L) {
                            throw errnoToIOException()
                        }
                        if (length < sizeOf<inotify_event>()) {
                            error("inotify got a short event.")
                        }
                        var offset = 0L
                        while (offset < length) {
                            val event = interpretPointed<inotify_event>(buffer.rawPtr + offset)
                            val path = if (event.len > 0u) event.name.toKString() else null
                            mutex.withLock { watchers[event.wd] }
                                ?.dispatchEvent(event.mask.toInt(), path)
                            offset += sizeOf<inotify_event>() + event.len.toLong()
                        }
                    }
                }
            }
        }
    }
}
package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.FileSystem
import okio.Path
import platform.linux.*
import platform.posix.EBADF
import platform.posix.EINTR
import platform.posix.NAME_MAX
import platform.posix.close
import platform.posix.errno
import platform.posix.read
import kotlin.sequences.forEach

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
    } or IN_DELETE_SELF or IN_MOVE_SELF // TODO Is this right?

    private val watchers = mutableMapOf<Int, Watcher>()
    private val mutex = Mutex()

    init {
        thread("FileWatcher", ::watch)
    }

    actual fun startWatching() = mutex.withLock {
        watchPath(path)
        if (recursive) {
            FileSystem.SYSTEM
                .listRecursively(path)
                .filter(FileSystem.SYSTEM::isDirectory)
                .forEach(::watchPath)
        }
    }

    private fun watchPath(path: Path) {
        val wd = inotify_add_watch(
            inotifyFd,
            path.toString(),
            eventsMask.toUInt()
        ).checkNotNegativeOne()
        watchers[wd] = Watcher(path) { event, resolvedPath ->
            if (recursive && event.mask.toInt() and IN_ISDIR != 0) {
                when (event.changeEvent) {
                    FileChangeEvent.Create -> mutex.withLock {
                        watchPath(resolvedPath)
                    }

                    FileChangeEvent.Delete -> mutex.withLock {
                        watchers.entries.removeAll {
                            if (it.value.path.startsWith(resolvedPath)) {
                                inotify_rm_watch(inotifyFd, it.key)
                                    .ensureSuccess()
                                true
                            } else false
                        }
                    }

                    else -> {}
                }
            }
            onEvent(event.changeEvent ?: return@Watcher, resolvedPath)
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
                print((if (event.len > 0u) event.name.toKString() else null)?.let(path::resolve) ?: path)
                if (event.mask.toInt() and IN_CREATE != 0)
                    print(" created")
                if (event.mask.toInt() and IN_MODIFY != 0)
                    print(" modified")
                if (event.mask.toInt() and IN_ATTRIB != 0)
                    print(" attributes")
                if (event.mask.toInt() and IN_DELETE != 0)
                    print(" removed")
                if (event.mask.toInt() and IN_MOVED_FROM != 0)
                    print(" renamed from")
                if (event.mask.toInt() and IN_MOVED_TO != 0)
                    print(" renamed to")
                if (event.mask.toInt() and IN_DELETE_SELF != 0)
                    print(" removed self")
                if (event.mask.toInt() and IN_MOVE_SELF != 0)
                    print(" moved self")
                println()
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
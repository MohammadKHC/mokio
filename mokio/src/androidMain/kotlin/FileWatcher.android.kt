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
        startWatching(path)

    actual fun stopWatching() = synchronized(observers) {
        observers.forEach { it.value.stopWatching() }
        observers.clear()
    }

    private fun dispatchEvent(eventMask: Int, path: Path) {
        val event = eventMask.changeEvent ?: return
        if (recursive && eventMask and IS_DIR == IS_DIR) {
            if (event == FileChangeEvent.Create) {
                startWatching(path)
            } else if (event == FileChangeEvent.Delete) {
                synchronized(observers) {
                    observers.entries.removeAll {
                        if (it.key.startsWith(path)) {
                            it.value.stopWatching()
                            true
                        } else false
                    }
                }
            }
        }
        onEvent(event, path)
    }

    private fun startWatching(path: Path) = synchronized(observers) {
        if (recursive && path.toFile().isDirectory) {
            path.toFile().walk().forEach {
                if (it.isDirectory) {
                    startObserver(it.toOkioPath())
                }
            }
        } else startObserver(path)
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
        const val IS_DIR = 0x40000000

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
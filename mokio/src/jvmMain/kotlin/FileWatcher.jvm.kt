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

    actual fun startWatching() {
        synchronized(watchers) {
            watchPath(path)
            if (emulateRecursive) {
                FileSystem.SYSTEM
                    .listRecursively(path)
                    .filter(FileSystem.SYSTEM::isDirectory)
                    .forEach(::watchPath)
            }
        }
        thread(name = "FileWatcher", block = ::watch)
    }

    private fun watchPath(path: Path) {
        val key = path.toNioPath().run {
            if (recursive) {
                try {
                    register(service, events, ExtendedWatchEventModifier.FILE_TREE)
                } catch (_: UnsupportedOperationException) {
                    emulateRecursive = true
                    register(service, events)
                }
            } else register(service, events)
        }
        watchers[key] = Watcher(path) { event, resolvedPath ->
            if (emulateRecursive && resolvedPath.toNioPath().isDirectory()) {
                when (event) {
                    FileChangeEvent.Create -> synchronized(watchers) {
                        watchPath(resolvedPath)
                    }

                    FileChangeEvent.Delete -> synchronized(watchers) {
                        watchers.entries.removeAll {
                            if (it.value.path.startsWith(resolvedPath)) {
                                it.key.cancel()
                                true
                            } else false
                        }
                    }

                    else -> {}
                }
            }
            onEvent(event, resolvedPath)
        }
    }

    actual fun stopWatching() {
        synchronized(watchers) {
            watchers.keys.forEach { it.cancel() }
            watchers.clear()
        }
        service.close()
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
    }
}
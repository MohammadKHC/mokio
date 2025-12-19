package com.mohammedkhc.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Path as NioPath

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val events = events.map {
        it.watchServiceEvent
    }.toTypedArray()

    private var key: WatchKey? = null

    actual fun startWatching() = startWatching(this)
    actual fun stopWatching() = stopWatching(this)

    fun dispatchEvent(event: FileChangeEvent, path: Path) {
        onEvent(event, path)
    }

    private val FileChangeEvent.watchServiceEvent
        get() = when (this) {
            FileChangeEvent.Create -> StandardWatchEventKinds.ENTRY_CREATE
            FileChangeEvent.Modify -> StandardWatchEventKinds.ENTRY_MODIFY
            FileChangeEvent.Delete -> StandardWatchEventKinds.ENTRY_DELETE
            FileChangeEvent.Attributes -> TODO()
        }

    private companion object {
        val service: WatchService = FileSystems.getDefault().newWatchService()
        val keys = mutableMapOf<WatchKey, FileWatcher>()
        var watcherJob: Job? = null
        val scope = CoroutineScope(Dispatchers.IO)
        val mutex = Mutex()

        fun startWatching(watcher: FileWatcher) {
            scope.launch {
                mutex.withLock {
                    val key = watcher.path.toNioPath().register(service, watcher.events)
                    watcher.key = key
                    keys[key] = watcher
                    if (watcherJob == null) {
                        watchEvents()
                    }
                }
            }
        }

        fun watchEvents() {
            watcherJob = scope.launch {
                while (true) {
                    val key = try {
                        service.take()
                    } catch (_: ClosedWatchServiceException) {
                        break
                    } catch (_: InterruptedException) {
                        break
                    }

                    val watcher = mutex.withLock { keys[key] } ?: continue

                    val events = key.pollEvents()
                    for (event in events) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue
                        val eventPath = event.context() as? NioPath ?: continue
                        val path = watcher.path / eventPath.toOkioPath()

                        watcher.dispatchEvent(
                            kind.changeEvent ?: continue,
                            path
                        )
                    }

                    if (!key.reset()) {
                        key.cancel()
                        mutex.withLock { keys -= key }
                    }
                }
            }
        }

        fun stopWatching(watcher: FileWatcher) {
            scope.launch {
                mutex.withLock {
                    val key = watcher.key ?: return@launch
                    key.cancel()
                    keys -= key
                    watcher.key = null
                    if (keys.isEmpty()) {
                        watcherJob = null
                    }
                }
            }
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
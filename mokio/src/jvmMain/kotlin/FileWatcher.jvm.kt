package com.mohammedkhc.io

import okio.Path

actual class FileWatcher actual constructor(
    path: Path,
    recursive: Boolean,
    events: Set<FileChangeEvent>,
    onEvent: FileEventListener
) {
    @Suppress("unused")
    private val handle = init(path.toString(), recursive, events, onEvent)
    external fun init(path: String, recursive: Boolean, events: Set<FileChangeEvent>, onEvent: FileEventListener): Long
    actual external fun startWatching()
    actual external fun stopWatching()

    companion object {
        init {
            JniLibraryLoader.load()
        }
    }
}
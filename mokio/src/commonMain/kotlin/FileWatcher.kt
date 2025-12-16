package com.mohammedkhc.io

import okio.Path

expect class FileWatcher(
    path: Path,
    recursive: Boolean = false,
    events: Set<FileChangeEvent> = FileChangeEvent.entries.toSet(),
    onEvent: FileEventListener
) {
    fun startWatching()
    fun stopWatching()
}

enum class FileChangeEvent {
    Create,
    Modify,
    Delete
}

typealias FileEventListener = (event: FileChangeEvent, path: Path) -> Unit
package com.mohammedkhc.io

import okio.Path

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    actual fun startWatching() {
    }

    actual fun stopWatching() {
    }
}
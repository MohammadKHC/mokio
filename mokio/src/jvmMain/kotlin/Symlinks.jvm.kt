package com.mohammedkhc.io

import okio.Path
import java.nio.file.Files
import java.nio.file.Paths

internal actual fun systemCreateSymbolicLink(source: Path, target: String) {
    Files.createSymbolicLink(source.toNioPath(), Paths.get(target))
}

internal actual fun systemReadSymbolicLink(symlink: Path): String =
    Files.readSymbolicLink(symlink.toNioPath()).toString()
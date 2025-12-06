package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import platform.posix.PATH_MAX
import platform.posix.readlink
import platform.posix.symlink

actual fun systemCreateSymbolicLink(source: Path, target: String) =
    symlink(target, source.toString()).ensureSuccess()

actual fun systemReadSymbolicLink(symlink: Path): String = memScoped {
    val buffer = allocArray<ByteVar>(PATH_MAX)
    if (readlink(symlink.toString(), buffer, PATH_MAX.convert()) == -1L) {
        throw errnoToIOException()
    }
    return buffer.toKString()
}
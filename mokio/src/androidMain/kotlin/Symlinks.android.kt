package com.mohammedkhc.io

import android.system.ErrnoException
import android.system.Os
import okio.Path

internal actual fun systemCreateSymbolicLink(source: Path, target: String) = try {
    Os.symlink(target, source.toString())
} catch (e: ErrnoException) {
    throw e.toIOException()
}

internal actual fun systemReadSymbolicLink(symlink: Path): String = try {
    Os.readlink(symlink.toString())
} catch (e: ErrnoException) {
    throw e.toIOException()
}
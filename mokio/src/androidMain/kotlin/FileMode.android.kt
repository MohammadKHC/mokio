package com.mohammedkhc.io

import android.system.ErrnoException
import android.system.Os
import okio.Path

internal actual fun systemSetFileMode(path: Path, mode: FileMode) = try {
    Os.chmod(path.toString(), mode.rawMode.toInt())
} catch (e: ErrnoException) {
    throw e.toIOException()
}
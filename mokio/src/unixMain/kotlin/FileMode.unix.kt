package com.mohammedkhc.io

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import okio.Path
import platform.posix.chmod

@OptIn(UnsafeNumber::class)
actual fun systemSetFileMode(path: Path, mode: FileMode) =
    chmod(path.toString(), mode.rawMode.convert())
        .ensureSuccess()
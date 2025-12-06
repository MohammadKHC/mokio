package com.mohammedkhc.io

import platform.posix.stat

internal actual val stat.deviceId get() = st_dev.toUInt()
internal actual val stat.changeTime get() = st_ctim
internal actual val stat.modificationTime get() = st_mtim
package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import platform.posix.lstat
import platform.posix.stat
import platform.posix.timespec
import kotlin.time.Instant

@OptIn(UnsafeNumber::class)
actual fun systemStat(path: Path, followSymlinks: Boolean): Stat = memScoped {
    val stat = alloc<stat>()
    val result =
        if (followSymlinks) stat(path.toString(), stat.ptr)
        else lstat(path.toString(), stat.ptr)
    result.ensureSuccess()

    stat.run {
        Stat(
            deviceId = deviceId,
            inode = st_ino.convert(),
            mode = FileMode(st_mode.convert()),
            userId = st_uid,
            groupId = st_gid,
            size = st_size,
            changeTime = changeTime.toInstant(),
            modificationTime = modificationTime.toInstant()
        )
    }
}

internal expect val stat.deviceId: UInt
internal expect val stat.changeTime: timespec
internal expect val stat.modificationTime: timespec

private fun timespec.toInstant() =
    Instant.fromEpochSeconds(tv_sec, tv_nsec)
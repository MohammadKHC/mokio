package com.mohammedkhc.io

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import okio.Path
import kotlin.time.Instant

internal actual fun systemStat(path: Path, followSymlinks: Boolean): Stat = try {
    val stat =
        if (followSymlinks) Os.stat(path.toString())
        else Os.lstat(path.toString())
    stat.run {
        Stat(
            deviceId = st_dev.toUInt(),
            inode = st_ino.toUInt(),
            mode = FileMode(st_mode.toUInt()),
            userId = st_uid.toUInt(),
            groupId = st_gid.toUInt(),
            size = st_size,
            changeTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                Instant.fromEpochSeconds(st_ctim.tv_sec, st_ctim.tv_nsec)
            } else {
                Instant.fromEpochMilliseconds(st_ctime)
            },
            modificationTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                Instant.fromEpochSeconds(st_mtim.tv_sec, st_mtim.tv_nsec)
            } else {
                Instant.fromEpochMilliseconds(st_mtime)
            }
        )
    }
} catch (e: ErrnoException) {
    throw e.toIOException()
}
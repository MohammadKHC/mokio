package com.mohammedkhc.io

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.time.Instant

data class Stat(
    val deviceId: UInt,
    val inode: UInt,
    val mode: FileMode,
    val userId: UInt,
    val groupId: UInt,
    val size: Long,
    val changeTime: Instant,
    val modificationTime: Instant
)

fun FileSystem.stat(path: Path, followSymlinks: Boolean = true): Stat = when (this) {
    FileSystem.SYSTEM -> systemStat(path, followSymlinks)
    else -> fallbackStat(path, followSymlinks)
}

internal expect fun systemStat(path: Path, followSymlinks: Boolean): Stat

/**
 * Fallback stat implementation for non system FileSystems.
 */
private fun FileSystem.fallbackStat(path: Path, followSymlinks: Boolean): Stat {
    val metadata = if (followSymlinks) {
        getRealPath(path, ::metadata) { it.symlinkTarget }
    } else {
        metadata(path).let {
            val symlinkTarget = it.symlinkTarget
            if (symlinkTarget == null) it
            else it.copy(size = symlinkTarget.toString().length.toLong())
        }
    }

    return metadata.run {
        Stat(
            deviceId = 0u,
            inode = 0u,
            mode = FileMode(
                when {
                    isRegularFile -> FileMode.Type.RegularFile
                    isDirectory -> FileMode.Type.Directory
                    symlinkTarget != null -> FileMode.Type.SymbolicLink
                    else -> FileMode.Type.RegularFile
                }, FileMode.Permission.DEFAULT
            ),
            userId = 0u,
            groupId = 0u,
            size = size ?: 0,
            changeTime = Instant.fromEpochMilliseconds(createdAtMillis ?: 0),
            modificationTime = Instant.fromEpochMilliseconds(lastModifiedAtMillis ?: 0)
        )
    }
}
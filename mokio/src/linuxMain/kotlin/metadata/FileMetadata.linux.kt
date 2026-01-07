/*
 * Copyright 2026 MohammedKHC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mohammedkhc.io.metadata

import com.mohammedkhc.io.FileMode
import com.mohammedkhc.io.ensureSuccess
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path
import platform.linuxx.*
import platform.posix.*
import kotlin.time.Instant

internal actual fun systemFileMetadata(
    path: Path,
    followLinks: Boolean
): FileMetadata = statx(path, followLinks) ?: stat(path, followLinks)

private fun statx(
    path: Path,
    followLinks: Boolean
): FileMetadata? = memScoped {
    val statx = alloc<statx>()
    val result = syscall(
        SYS_statx.toLong(),
        AT_FDCWD,
        path.toString(),
        if (followLinks) 0 else AT_SYMLINK_NOFOLLOW,
        STATX_BASIC_STATS or STATX_BTIME,
        statx.ptr
    ).toInt()
    if (result == -1 && errno == ENOSYS) {
        return null
    }
    result.ensureSuccess()

    with(statx) {
        val lastModifiedTime = stx_mtime.toInstant()
        UnixFileMetadata(
            deviceId = stx_dev_major.toLong(),
            inode = stx_ino.toLong(),
            mode = FileMode(stx_mode.toUInt()),
            linkCount = stx_nlink.toInt(),
            userId = stx_uid,
            groupId = stx_gid,
            rawDeviceId = stx_rdev_major.toLong(),
            changeTime = stx_ctime.toInstant(),
            creationTime =
                if (stx_mask and STATX_BTIME != 0u) stx_btime.toInstant()
                else lastModifiedTime,
            lastModifiedTime = lastModifiedTime,
            lastAccessTime = stx_atime.toInstant(),
            size = stx_size.toLong()
        )
    }
}

@OptIn(UnsafeNumber::class)
private fun stat(
    path: Path,
    followLinks: Boolean
): FileMetadata = memScoped {
    val stat = alloc<stat>()
    val result =
        if (followLinks) stat(path.toString(), stat.ptr)
        else lstat(path.toString(), stat.ptr)
    result.ensureSuccess()

    with(stat) {
        val lastModifiedTime = st_mtim.toInstant()
        UnixFileMetadata(
            deviceId = st_dev.toLong(),
            inode = st_ino.toLong(),
            mode = FileMode(st_mode),
            linkCount = st_nlink.toInt(),
            userId = st_uid,
            groupId = st_gid,
            rawDeviceId = st_rdev.toLong(),
            changeTime = st_ctim.toInstant(),
            creationTime = lastModifiedTime,
            lastModifiedTime = lastModifiedTime,
            lastAccessTime = st_atim.toInstant(),
            size = st_size
        )
    }
}

private fun statx_timestamp.toInstant() =
    Instant.fromEpochSeconds(tv_sec, tv_nsec.toLong())

private const val AT_FDCWD = -100
private const val AT_SYMLINK_NOFOLLOW = 0x100
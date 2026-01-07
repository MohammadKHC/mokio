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

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import com.mohammedkhc.io.FileMode
import com.mohammedkhc.io.toIOException
import okio.Path
import kotlin.time.Instant

internal actual fun systemFileMetadata(
    path: Path,
    followLinks: Boolean
): FileMetadata = try {
    val stat =
        if (followLinks) Os.stat(path.toString())
        else Os.lstat(path.toString())
    with(stat) {
        val changeTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Instant.fromEpochSeconds(st_ctim.tv_sec, st_ctim.tv_nsec)
        } else {
            Instant.fromEpochSeconds(st_ctime)
        }
        val lastModifiedTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Instant.fromEpochSeconds(st_mtim.tv_sec, st_mtim.tv_nsec)
        } else {
            Instant.fromEpochSeconds(st_mtime)
        }
        val lastAccessTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Instant.fromEpochSeconds(st_atim.tv_sec, st_atim.tv_nsec)
        } else {
            Instant.fromEpochSeconds(st_atime)
        }

        UnixFileMetadata(
            deviceId = st_dev,
            inode = st_ino,
            mode = FileMode(st_mode.toUInt()),
            linkCount = st_nlink.toInt(),
            userId = st_uid.toUInt(),
            groupId = st_gid.toUInt(),
            rawDeviceId = st_rdev,
            changeTime = changeTime,
            // Android doesn't support birthtime. fallback to lastModifiedTime.
            creationTime = lastModifiedTime,
            lastModifiedTime = lastModifiedTime,
            lastAccessTime = lastAccessTime,
            size = st_size
        )
    }
} catch (e: ErrnoException) {
    throw e.toIOException()
}
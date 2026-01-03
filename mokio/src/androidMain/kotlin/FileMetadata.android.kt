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

package com.mohammedkhc.io

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.mohammedkhc.io.FileMetadata.Attribute
import okio.IOException
import okio.Path
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import kotlin.io.path.fileAttributesView
import kotlin.time.Instant
import kotlin.time.toJavaInstant

internal actual fun systemFileMetadata(path: Path, followLinks: Boolean): FileMetadata = try {
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

        FileMetadata.Unix(
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

internal actual fun systemSetFileMetadata(
    path: Path,
    followLinks: Boolean,
    vararg attributes: Attribute
) {
    try {
        var setTimes = false

        for (attribute in attributes) {
            when (attribute) {
                is Attribute.Time -> setTimes = true

                is Attribute.Unix.FileMode -> if (followLinks) {
                    Os.chmod(path.toString(), attribute.mode.rawMode.toInt())
                } else {
                    val fd = Os.open(path.toString(), OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
                    try {
                        Os.fchmod(fd, attribute.mode.rawMode.toInt())
                    } finally {
                        Os.close(fd)
                    }
                }

                is Attribute.Unix.Owner -> if (followLinks) {
                    Os.chown(path.toString(), attribute.userId.toInt(), attribute.groupId.toInt())
                } else {
                    Os.lchown(path.toString(), attribute.userId.toInt(), attribute.groupId.toInt())
                }

                is Attribute.Windows -> throw UnsupportedOperationException()
            }
        }

        if (setTimes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options =
                    if (followLinks) emptyArray()
                    else arrayOf(LinkOption.NOFOLLOW_LINKS)
                val basicView = path.toNioPath().fileAttributesView<BasicFileAttributeView>(*options)
                basicView.setTimes(
                    attributes.get<Attribute.Time.LastModified>()
                        ?.time?.toJavaInstant()?.let(FileTime::from),
                    attributes.get<Attribute.Time.LastAccess>()
                        ?.time?.toJavaInstant()?.let(FileTime::from),
                    null
                )
            } else {
                attributes.get<Attribute.Time.LastModified>()?.let {
                    if (!path.toFile().setLastModified(it.time.toEpochMilliseconds())) {
                        throw IOException("Failed to set last modified time for file: $path")
                    }
                }
                if (attributes.get<Attribute.Time.LastAccess>() != null) {
                    throw UnsupportedOperationException("Changing the file last access time is not supported on this Android version.")
                }
            }
            if (attributes.get<Attribute.Time.Creation>() != null) {
                throw UnsupportedOperationException("Changing the file creation time is not supported on Android.")
            }
        }
    } catch (e: ErrnoException) {
        throw e.toIOException()
    }
}
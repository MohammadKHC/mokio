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
import android.system.OsConstants
import com.mohammedkhc.io.toIOException
import okio.IOException
import okio.Path
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileTime
import kotlin.io.path.fileAttributesView
import kotlin.time.toJavaInstant

internal actual fun systemSetFileAttributes(
    path: Path,
    attributes: Array<out FileAttribute>,
    followLinks: Boolean
) {
    try {
        var setTimes = false

        for (attribute in attributes) {
            when (attribute) {
                is FileTimeAttribute -> setTimes = true

                is FileModeAttribute -> if (followLinks) {
                    Os.chmod(path.toString(), attribute.mode.rawMode.toInt())
                } else {
                    val fd = Os.open(path.toString(), OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
                    try {
                        Os.fchmod(fd, attribute.mode.rawMode.toInt())
                    } finally {
                        Os.close(fd)
                    }
                }

                is FileOwnerAttribute -> if (followLinks) {
                    Os.chown(path.toString(), attribute.userId.toInt(), attribute.groupId.toInt())
                } else {
                    Os.lchown(path.toString(), attribute.userId.toInt(), attribute.groupId.toInt())
                }

                is WindowsFileAttribute -> throw UnsupportedOperationException()
            }
        }

        if (setTimes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options =
                    if (followLinks) emptyArray()
                    else arrayOf(LinkOption.NOFOLLOW_LINKS)
                val basicView = path.toNioPath().fileAttributesView<BasicFileAttributeView>(*options)
                basicView.setTimes(
                    attributes.get<LastModifiedTime>()
                        ?.time?.toJavaInstant()?.let(FileTime::from),
                    attributes.get<LastAccessTime>()
                        ?.time?.toJavaInstant()?.let(FileTime::from),
                    null
                )
            } else {
                attributes.get<LastModifiedTime>()?.let {
                    if (!path.toFile().setLastModified(it.time.toEpochMilliseconds())) {
                        throw IOException("Failed to set last modified time for file: $path")
                    }
                }
                if (attributes.get<LastAccessTime>() != null) {
                    throw UnsupportedOperationException("Changing the file last access time is not supported on this Android version.")
                }
            }
            if (attributes.get<CreationTime>() != null) {
                throw UnsupportedOperationException("Changing the file creation time is not supported on Android.")
            }
        }
    } catch (e: ErrnoException) {
        throw e.toIOException()
    }
}
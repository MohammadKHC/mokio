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
import okio.Path
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.io.path.readAttributes
import kotlin.time.toKotlinInstant

internal actual fun systemFileMetadata(
    path: Path,
    followLinks: Boolean
): FileMetadata {
    val nioPath = path.toNioPath()
    val options =
        if (followLinks) emptyArray()
        else arrayOf(LinkOption.NOFOLLOW_LINKS)
    val supportedAttributeViews = FileSystems.getDefault().supportedFileAttributeViews()

    return when {
        "unix" in supportedAttributeViews ->
            with(nioPath.readAttributes("unix:*", *options)) {
                UnixFileMetadata(
                    deviceId = get("dev") as Long,
                    inode = get("ino") as Long,
                    mode = FileMode((get("mode") as Int).toUInt()),
                    linkCount = get("nlink") as Int,
                    userId = (get("uid") as Int).toUInt(),
                    groupId = (get("gid") as Int).toUInt(),
                    rawDeviceId = get("rdev") as Long,
                    changeTime = (get("ctime") as FileTime).toInstant().toKotlinInstant(),
                    creationTime = (get("creationTime") as FileTime).toInstant().toKotlinInstant(),
                    lastModifiedTime = (get("lastModifiedTime") as FileTime).toInstant().toKotlinInstant(),
                    lastAccessTime = (get("lastAccessTime") as FileTime).toInstant().toKotlinInstant(),
                    size = get("size") as Long
                )
            }

        "dos" in supportedAttributeViews ->
            with(nioPath.readAttributes<DosFileAttributes>(*options)) {
                WindowsFileMetadata(
                    isReadOnly = isReadOnly,
                    isArchive = isArchive,
                    isSystem = isSystem,
                    isHidden = isHidden,
                    isRegularFile = isRegularFile,
                    isDirectory = isDirectory,
                    isSymbolicLink = isSymbolicLink,
                    isOther = isOther,
                    creationTime = creationTime().toInstant().toKotlinInstant(),
                    lastModifiedTime = lastModifiedTime().toInstant().toKotlinInstant(),
                    lastAccessTime = lastAccessTime().toInstant().toKotlinInstant(),
                    size = size()
                )
            }

        else ->
            with(nioPath.readAttributes<BasicFileAttributes>(*options)) {
                BasicFileMetadata(
                    isRegularFile = isRegularFile,
                    isDirectory = isDirectory,
                    isSymbolicLink = isSymbolicLink,
                    isOther = isOther,
                    creationTime = creationTime().toInstant().toKotlinInstant(),
                    lastModifiedTime = lastModifiedTime().toInstant().toKotlinInstant(),
                    lastAccessTime = lastAccessTime().toInstant().toKotlinInstant(),
                    size = size()
                )
            }
    }
}
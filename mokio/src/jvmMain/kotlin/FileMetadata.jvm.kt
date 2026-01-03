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

import com.mohammedkhc.io.FileMetadata.Attribute
import okio.Path
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.nio.file.attribute.*
import kotlin.io.path.fileAttributesView
import kotlin.io.path.readAttributes
import kotlin.io.path.setAttribute
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

internal actual fun systemFileMetadata(path: Path, followLinks: Boolean): FileMetadata {
    val nioPath = path.toNioPath()
    val options =
        if (followLinks) emptyArray()
        else arrayOf(LinkOption.NOFOLLOW_LINKS)
    val supportedAttributeViews = FileSystems.getDefault().supportedFileAttributeViews()

    return when {
        "unix" in supportedAttributeViews ->
            with(nioPath.readAttributes("unix:*", *options)) {
                FileMetadata.Unix(
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
                FileMetadata.Windows(
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
                FileMetadata.Basic(
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

internal actual fun systemSetFileMetadata(
    path: Path,
    followLinks: Boolean,
    vararg attributes: Attribute
) {
    val nioPath = path.toNioPath()
    val options =
        if (followLinks) emptyArray()
        else arrayOf(LinkOption.NOFOLLOW_LINKS)

    var setTimes = false

    for (attribute in attributes) {
        when (attribute) {
            is Attribute.Time -> setTimes = true

            is Attribute.Unix.FileMode ->
                nioPath.setAttribute("unix:mode", attribute.mode.rawMode.toInt(), *options)

            is Attribute.Unix.Owner -> {
                nioPath.setAttribute("unix:uid", attribute.userId.toInt(), *options)
                nioPath.setAttribute("unix:gid", attribute.groupId.toInt(), *options)
            }

            is Attribute.Windows.ReadOnly ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setReadOnly(attribute.enabled)

            is Attribute.Windows.Archive ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setArchive(attribute.enabled)

            is Attribute.Windows.System ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setSystem(attribute.enabled)

            is Attribute.Windows.Hidden ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setHidden(attribute.enabled)
        }
    }

    if (setTimes) {
        val basicView = nioPath.fileAttributesView<BasicFileAttributeView>(*options)
        basicView.setTimes(
            attributes.get<Attribute.Time.LastModified>()
                ?.time?.toJavaInstant()?.let(FileTime::from),
            attributes.get<Attribute.Time.LastAccess>()
                ?.time?.toJavaInstant()?.let(FileTime::from),
            attributes.get<Attribute.Time.Creation>()
                ?.time?.toJavaInstant()?.let(FileTime::from),
        )
        if (isLinux && attributes.get<Attribute.Time.Creation>() != null) {
            throw UnsupportedOperationException("Changing the file creation time is not supported on Linux.")
        }
    }
}

private val isLinux by lazy {
    System.getProperty("os.name").startsWith("Linux")
}
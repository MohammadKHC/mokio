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

import okio.Path
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import kotlin.io.path.fileAttributesView
import kotlin.io.path.setAttribute
import kotlin.time.toJavaInstant

internal actual fun systemSetFileAttributes(
    path: Path,
    attributes: Array<out FileAttribute>,
    followLinks: Boolean
) {
    val nioPath = path.toNioPath()
    val options =
        if (followLinks) emptyArray()
        else arrayOf(LinkOption.NOFOLLOW_LINKS)

    var setTimes = false

    for (attribute in attributes) {
        when (attribute) {
            is FileTimeAttribute -> setTimes = true

            is FileModeAttribute ->
                nioPath.setAttribute("unix:mode", attribute.mode.rawMode.toInt(), *options)

            is FileOwnerAttribute -> {
                nioPath.setAttribute("unix:uid", attribute.userId.toInt(), *options)
                nioPath.setAttribute("unix:gid", attribute.groupId.toInt(), *options)
            }

            is WindowsFileAttribute.ReadOnly ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setReadOnly(attribute.enabled)

            is WindowsFileAttribute.Archive ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setArchive(attribute.enabled)

            is WindowsFileAttribute.System ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setSystem(attribute.enabled)

            is WindowsFileAttribute.Hidden ->
                nioPath.fileAttributesView<DosFileAttributeView>(*options).setHidden(attribute.enabled)
        }
    }

    if (setTimes) {
        val basicView = nioPath.fileAttributesView<BasicFileAttributeView>(*options)
        val creationTime = attributes.get<CreationTime>()
            ?.time?.toJavaInstant()?.let(FileTime::from)
        basicView.setTimes(
            attributes.get<LastModifiedTime>()
                ?.time?.toJavaInstant()?.let(FileTime::from),
            attributes.get<LastAccessTime>()
                ?.time?.toJavaInstant()?.let(FileTime::from),
            creationTime
        )
        if (creationTime != null && System.getProperty("os.name").startsWith("Linux")) {
            throw UnsupportedOperationException("Changing the file creation time is not supported on Linux.")
        }
    }
}
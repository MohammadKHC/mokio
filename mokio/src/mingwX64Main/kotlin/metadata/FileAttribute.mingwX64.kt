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

import com.mohammedkhc.io.ensureSuccess
import com.mohammedkhc.io.getFileInformation
import com.mohammedkhc.io.useHandle
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import okio.Path
import platform.windows.*
import kotlin.time.Instant

internal actual fun systemSetFileAttributes(
    path: Path,
    attributes: Array<out FileAttribute>,
    followLinks: Boolean
) = useHandle(path, followLinks, FILE_WRITE_ATTRIBUTES) {
    var fileAttributes = 0u
    var creationTime = -1L
    var lastModifiedTime = -1L
    var lastAccessTime = -1L

    for (attribute in attributes) {
        when (attribute) {
            is CreationTime ->
                creationTime = attribute.time.toFileTimeQuad()

            is LastModifiedTime ->
                lastModifiedTime = attribute.time.toFileTimeQuad()

            is LastAccessTime ->
                lastAccessTime = attribute.time.toFileTimeQuad()

            is WindowsFileAttribute -> {
                val flag = when (attribute) {
                    is WindowsFileAttribute.ReadOnly -> FILE_ATTRIBUTE_READONLY
                    is WindowsFileAttribute.Archive -> FILE_ATTRIBUTE_ARCHIVE
                    is WindowsFileAttribute.System -> FILE_ATTRIBUTE_SYSTEM
                    is WindowsFileAttribute.Hidden -> FILE_ATTRIBUTE_HIDDEN
                }
                fileAttributes =
                    if (attribute.enabled) fileAttributes or flag.toUInt()
                    else fileAttributes and flag.toUInt().inv()
            }

            is UnixFileAttribute -> throw UnsupportedOperationException()
        }
    }

    memScoped {
        SetFileInformationByHandle(
            this@useHandle,
            FILE_INFO_BY_HANDLE_CLASS.FileBasicInfo,
            alloc<FILE_BASIC_INFO> {
                FileAttributes = if (fileAttributes != 0u) {
                    this@useHandle.getFileInformation().dwFileAttributes or fileAttributes
                } else 0u
                CreationTime.QuadPart = creationTime
                LastWriteTime.QuadPart = lastModifiedTime
                LastAccessTime.QuadPart = lastAccessTime
            }.ptr,
            sizeOf<FILE_BASIC_INFO>().toUInt()
        ).ensureSuccess()
    }
}

private fun Instant.toFileTimeQuad() =
    (((epochSeconds.toULong() + 11644473600UL) * 10_000_000UL) +
            (nanosecondsOfSecond.toULong() / 100UL)).toLong()
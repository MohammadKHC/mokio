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

import com.mohammedkhc.io.getFileInformation
import com.mohammedkhc.io.getReparseData
import com.mohammedkhc.io.useHandle
import okio.Path
import platform.windows.*
import kotlin.time.Instant

internal actual fun systemFileMetadata(
    path: Path,
    followLinks: Boolean
): FileMetadata = useHandle(path, followLinks, FILE_READ_ATTRIBUTES) {
    with(getFileInformation()) {
        val isDevice = dwFileAttributes.toInt() and FILE_ATTRIBUTE_DEVICE != 0
        val isDirectory = dwFileAttributes.toInt() and FILE_ATTRIBUTE_DIRECTORY != 0
        val isReparsePoint = dwFileAttributes.toInt() and FILE_ATTRIBUTE_REPARSE_POINT != 0

        val isSymbolicLink = isReparsePoint &&
                getReparseData().ReparseTag == IO_REPARSE_TAG_SYMLINK

        WindowsFileMetadata(
            isReadOnly = dwFileAttributes.toInt() and FILE_ATTRIBUTE_READONLY != 0,
            isArchive = dwFileAttributes.toInt() and FILE_ATTRIBUTE_ARCHIVE != 0,
            isSystem = dwFileAttributes.toInt() and FILE_ATTRIBUTE_SYSTEM != 0,
            isHidden = dwFileAttributes.toInt() and FILE_ATTRIBUTE_HIDDEN != 0,
            isRegularFile = !isReparsePoint && !isDevice && !isDirectory,
            isDirectory = isDirectory && !isReparsePoint,
            isSymbolicLink = isSymbolicLink,
            isOther = !isSymbolicLink && (isDirectory || isReparsePoint),
            creationTime = ftCreationTime.toInstant(),
            lastModifiedTime = ftLastWriteTime.toInstant(),
            lastAccessTime = ftLastAccessTime.toInstant(),
            size = combineToULong(nFileSizeHigh, nFileSizeLow).toLong()
        )
    }
}

private fun combineToULong(high: UInt, low: UInt) =
    (high.toULong() shl 32) or low.toULong()

private fun FILETIME.toInstant(): Instant {
    val time = combineToULong(dwHighDateTime, dwLowDateTime)
    return Instant.fromEpochSeconds(
        (time / 10_000_000UL).toLong() - 11644473600L,
        ((time % 10_000_000UL) * 100UL).toInt()
    )
}
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
import kotlinx.cinterop.*
import okio.Path
import platform.windows.*
import platform.windowsx.REPARSE_DATA_BUFFER
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Instant

internal actual fun systemFileMetadata(path: Path, followLinks: Boolean): FileMetadata {
    return useHandle(path, followLinks, FILE_READ_ATTRIBUTES) {
        with(getFileInformation()) {
            val isDevice = dwFileAttributes.toInt() and FILE_ATTRIBUTE_DEVICE != 0
            val isDirectory = dwFileAttributes.toInt() and FILE_ATTRIBUTE_DIRECTORY != 0
            val isReparsePoint = dwFileAttributes.toInt() and FILE_ATTRIBUTE_REPARSE_POINT != 0

            val isSymbolicLink = isReparsePoint &&
                    getReparseData().ReparseTag == IO_REPARSE_TAG_SYMLINK

            FileMetadata.Windows(
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
}

private fun HANDLE.getFileInformation(): BY_HANDLE_FILE_INFORMATION = memScoped {
    alloc {
        GetFileInformationByHandle(
           this@getFileInformation,
            ptr
        ).ensureSuccess()
    }
}

internal fun HANDLE.getReparseData(): REPARSE_DATA_BUFFER = memScoped {
    val size = MAXIMUM_REPARSE_DATA_BUFFER_SIZE
    val data = alloc(size, alignOf<REPARSE_DATA_BUFFER>())
        .reinterpret<REPARSE_DATA_BUFFER>()
    DeviceIoControl(
        hDevice = this@getReparseData,
        dwIoControlCode = FSCTL_GET_REPARSE_POINT.toUInt(),
        lpInBuffer = null,
        nInBufferSize = 0u,
        lpOutBuffer = data.ptr,
        nOutBufferSize = size.toUInt(),
        lpBytesReturned = null,
        lpOverlapped = null
    ).ensureSuccess()
    data
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

private fun Instant.toFileTimeQuad() =
    (((epochSeconds.toULong() + 11644473600UL) * 10_000_000UL) +
            (nanosecondsOfSecond.toULong() / 100UL)).toLong()

internal actual fun systemSetFileMetadata(
    path: Path,
    followLinks: Boolean,
    vararg attributes: Attribute
) = useHandle(path, followLinks, FILE_WRITE_ATTRIBUTES) {
    var fileAttributes = 0u
    var creationTime = -1L
    var lastModifiedTime = -1L
    var lastAccessTime = -1L

    for (attribute in attributes) {
        when (attribute) {
            is Attribute.Time.Creation ->
                creationTime = attribute.time.toFileTimeQuad()

            is Attribute.Time.LastModified ->
                lastModifiedTime = attribute.time.toFileTimeQuad()

            is Attribute.Time.LastAccess ->
                lastAccessTime = attribute.time.toFileTimeQuad()

            is Attribute.Windows -> {
                val flag = when (attribute) {
                    is Attribute.Windows.ReadOnly -> FILE_ATTRIBUTE_READONLY
                    is Attribute.Windows.Archive -> FILE_ATTRIBUTE_ARCHIVE
                    is Attribute.Windows.System -> FILE_ATTRIBUTE_SYSTEM
                    is Attribute.Windows.Hidden -> FILE_ATTRIBUTE_HIDDEN
                }
                fileAttributes =
                    if (attribute.enabled) fileAttributes or flag.toUInt()
                    else fileAttributes and flag.toUInt().inv()
            }

            is Attribute.Unix -> throw UnsupportedOperationException()
        }
    }

    memScoped {
        SetFileInformationByHandle(
            this@useHandle,
            FILE_INFO_BY_HANDLE_CLASS.FileBasicInfo,
            alloc<FILE_BASIC_INFO> {
                FileAttributes = fileAttributes
                CreationTime.QuadPart = creationTime
                LastWriteTime.QuadPart = lastModifiedTime
                LastAccessTime.QuadPart = lastAccessTime
            }.ptr,
            sizeOf<FILE_BASIC_INFO>().toUInt()
        ).ensureSuccess()
    }
}

@OptIn(ExperimentalContracts::class)
internal fun <T> useHandle(
    path: Path,
    followLinks: Boolean = true,
    desiredAccess: Int = 0,
    action: HANDLE.() -> T
): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    val handle = CreateFileW(
        lpFileName = path.toString(),
        dwDesiredAccess = desiredAccess.toUInt(),
        dwShareMode = FILE_SHARE_DELETE.toUInt() or FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt() or
                (if (followLinks) 0u else FILE_FLAG_OPEN_REPARSE_POINT.toUInt()),
        hTemplateFile = null
    )

    if (handle == INVALID_HANDLE_VALUE) {
        throw lastErrorToIOException()
    }
    try {
        return handle!!.action()
    } finally {
        CloseHandle(handle)
    }
}
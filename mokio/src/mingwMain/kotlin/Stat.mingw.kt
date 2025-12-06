package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import okio.Path.Companion.toPath
import platform.windows.*
import platform.windowsx.REPARSE_DATA_BUFFER
import kotlin.time.Instant

internal actual fun systemStat(path: Path, followSymlinks: Boolean): Stat {
    val metadata = if (followSymlinks) {
        getRealPath(path, WindowsFileMetadata::from) { it.symlinkTarget }
    } else {
        WindowsFileMetadata.from(path)
    }

    return metadata.run {
        Stat(
            deviceId = 0u,
            inode = 0u,
            mode = FileMode(
                when {
                    isRegularFile -> FileMode.Type.RegularFile
                    isDirectory -> FileMode.Type.Directory
                    symlinkTarget != null -> FileMode.Type.SymbolicLink
                    else -> FileMode.Type.RegularFile
                }, FileMode.Permission.DEFAULT
            ),
            userId = 0u,
            groupId = 0u,
            size = size,
            changeTime = creationTime,
            modificationTime = modificationTime
        )
    }
}

data class WindowsFileMetadata(
    val isRegularFile: Boolean,
    val isDirectory: Boolean,
    val size: Long,
    val creationTime: Instant,
    val modificationTime: Instant,
    val symlinkTarget: Path?,
) {
    companion object {
        fun from(path: Path): WindowsFileMetadata {
            val fileInfo = memScoped {
                alloc<WIN32_FILE_ATTRIBUTE_DATA> {
                    GetFileAttributesExW(
                        path.toString(),
                        _GET_FILEEX_INFO_LEVELS.GetFileExInfoStandard,
                        ptr
                    ).ensureSuccess()
                }
            }

            val isDevice = fileInfo.dwFileAttributes and FILE_ATTRIBUTE_DEVICE.toUInt() != 0u
            val isDirectory = fileInfo.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt() != 0u
            val isReparsePoint = fileInfo.dwFileAttributes and FILE_ATTRIBUTE_REPARSE_POINT.toUInt() != 0u

            val reparseData =
                if (isReparsePoint) getReparseData(path)
                else null

            val isSymbolicLink = reparseData?.ReparseTag == IO_REPARSE_TAG_SYMLINK
            val symlinkTarget = if (isSymbolicLink) reparseData.symlinkTarget else null

            return WindowsFileMetadata(
                isRegularFile = !isReparsePoint && !isDevice && !isDirectory,
                isDirectory = isDirectory && !isReparsePoint,
                size = symlinkTarget?.length?.toLong()
                    ?: combineToULong(fileInfo.nFileSizeHigh, fileInfo.nFileSizeLow).toLong(),
                creationTime = fileInfo.ftCreationTime.toInstant(),
                modificationTime = fileInfo.ftLastWriteTime.toInstant(),
                symlinkTarget = symlinkTarget?.toPath()
            )
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
    }
}

internal fun getReparseData(path: Path): REPARSE_DATA_BUFFER {
    val handle = CreateFileW(
        lpFileName = path.toString(),
        dwDesiredAccess = 0u,
        dwShareMode = FILE_SHARE_DELETE.toUInt() or FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt() or FILE_FLAG_OPEN_REPARSE_POINT.toUInt(),
        hTemplateFile = null
    )
    if (handle == INVALID_HANDLE_VALUE) {
        throw lastErrorToIOException()
    }

    try {
        memScoped {
            val size = MAXIMUM_REPARSE_DATA_BUFFER_SIZE
            val data = alloc(size, alignOf<REPARSE_DATA_BUFFER>())
                .reinterpret<REPARSE_DATA_BUFFER>()
            DeviceIoControl(
                hDevice = handle,
                dwIoControlCode = FSCTL_GET_REPARSE_POINT.toUInt(),
                lpInBuffer = null,
                nInBufferSize = 0u,
                lpOutBuffer = data.ptr,
                nOutBufferSize = size.toUInt(),
                lpBytesReturned = null,
                lpOverlapped = null
            ).ensureSuccess()
            return data
        }
    } finally {
        CloseHandle(handle)
    }
}

internal val REPARSE_DATA_BUFFER.symlinkTarget: String
    get() = SymbolicLinkReparseBuffer.run {
        val charSize = sizeOf<WCHARVar>().toInt()
        val offset = SubstituteNameOffset.toInt() / charSize
        CharArray(SubstituteNameLength.toInt() / charSize) {
            PathBuffer[offset + it].toInt().toChar()
        }.concatToString().removePrefix("\\??\\")
    }
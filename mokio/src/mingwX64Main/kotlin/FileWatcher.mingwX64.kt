package com.mohammedkhc.io

import kotlinx.cinterop.alignOf
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.sizeOf
import okio.Path
import platform.windows.CreateFileW
import platform.windows.DWORD
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.FILE_LIST_DIRECTORY
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.OPEN_EXISTING
import platform.windows.ReadDirectoryChangesW

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    actual fun startWatching() {
        val handle = CreateFileW(
            lpFileName = path.toString(),
            dwDesiredAccess = FILE_LIST_DIRECTORY.toUInt(),
            dwShareMode = FILE_SHARE_DELETE.toUInt() or FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(),
            lpSecurityAttributes = null,
            dwCreationDisposition = OPEN_EXISTING.toUInt(),
            dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt() or FILE_FLAG_OVERLAPPED.toUInt(),
            hTemplateFile = null
        )
        val buffer = nativeHeap.alloc(sizeOf<DWORD>(), alignOf<DWORD>())
        ReadDirectoryChangesW(
            hDirectory = handle,
            lpBuffer = null,
            nBufferLength = 1u,
            bWatchSubtree = if (recursive) 1 else 0,
            dwNotifyFilter = 0,
            lpBytesReturned = 0,
            lpOverlapped = null,
            lpCompletionRoutine =null
        )
    }

    actual fun stopWatching() {
    }
}
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

import kotlinx.cinterop.*
import okio.Path
import platform.windows.*

actual class FileWatcher actual constructor(
    private val path: Path,
    private val recursive: Boolean,
    private val events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private val handle = CreateFileW(
        lpFileName = path.toString(),
        dwDesiredAccess = FILE_LIST_DIRECTORY.toUInt(),
        dwShareMode = FILE_SHARE_DELETE.toUInt() or FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt(),
        hTemplateFile = null
    ) ?: throw lastErrorToIOException()

    actual fun startWatching() {
        thread("FileWatcher", ::watch)
    }

    private fun watch() = memScoped {
        val buffer = alloc(notifyBufferSize, alignOf<FILE_NOTIFY_INFORMATION>())
        while (true) {
            val length = memScoped {
                val lengthVar = alloc<UIntVar>()
                if (ReadDirectoryChangesW(
                        hDirectory = handle,
                        lpBuffer = buffer.reinterpret<DWORDVar>().ptr,
                        nBufferLength = notifyBufferSize.toUInt(),
                        bWatchSubtree = if (recursive) 1 else 0,
                        dwNotifyFilter = FILE_NOTIFY_ALL.toUInt(),
                        lpBytesReturned = lengthVar.ptr,
                        lpOverlapped = null,
                        lpCompletionRoutine = null
                    ) == 0
                ) {
                    if (GetLastError().toInt() == ERROR_INVALID_HANDLE) {
                        break
                    }
                    throw lastErrorToIOException()
                }
                lengthVar.value.toLong()
            }
            if (length < sizeOf<FILE_NOTIFY_INFORMATION>()) {
                error("ReadDirectoryChanges got a short event.")
            }
            var offset = 0L
            while (offset < length) {
                val event = interpretPointed<FILE_NOTIFY_INFORMATION>(buffer.rawPtr + offset)
                val path = CharArray(event.FileNameLength.toInt() / 2) {
                    event.FileName[it].toInt().toChar()
                }.concatToString().let(path::resolve)
                when (event.Action.toInt()) {
                    FILE_ACTION_ADDED,
                    FILE_ACTION_RENAMED_NEW_NAME -> if (FileChangeEvent.Create in events) {
                        onEvent(FileChangeEvent.Create, path)
                    }

                    FILE_ACTION_MODIFIED -> if (FileChangeEvent.Modify in events) {
                        onEvent(FileChangeEvent.Modify, path)
                    }

                    FILE_ACTION_REMOVED,
                    FILE_ACTION_RENAMED_OLD_NAME -> if (FileChangeEvent.Delete in events) {
                        onEvent(FileChangeEvent.Delete, path)
                    }
                }
                if (event.NextEntryOffset == 0u) break
                offset += event.NextEntryOffset.toLong()
            }
        }
    }

    actual fun stopWatching() {
        CloseHandle(handle).ensureSuccess()
    }

    private companion object {
        const val FILE_NOTIFY_ALL = FILE_NOTIFY_CHANGE_FILE_NAME or
                FILE_NOTIFY_CHANGE_DIR_NAME or
                FILE_NOTIFY_CHANGE_ATTRIBUTES or
                FILE_NOTIFY_CHANGE_SIZE or
                FILE_NOTIFY_CHANGE_LAST_WRITE or
                FILE_NOTIFY_CHANGE_LAST_ACCESS or
                FILE_NOTIFY_CHANGE_CREATION or
                FILE_NOTIFY_CHANGE_SECURITY
        val notifyBufferSize = 5 * (sizeOf<FILE_NOTIFY_INFORMATION>() + 255 + 1)
    }
}
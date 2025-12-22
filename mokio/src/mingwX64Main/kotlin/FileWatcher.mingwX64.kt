package com.mohammedkhc.io

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alignOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretPointed
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import okio.Path
import platform.posix.EBADF
import platform.posix.EINTR
import platform.posix.close
import platform.posix.errno
import platform.posix.read
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.DWORD
import platform.windows.DWORDVar
import platform.windows.ERROR_INVALID_HANDLE
import platform.windows.FILE_ACTION_ADDED
import platform.windows.FILE_ACTION_MODIFIED
import platform.windows.FILE_ACTION_REMOVED
import platform.windows.FILE_ACTION_RENAMED_NEW_NAME
import platform.windows.FILE_ACTION_RENAMED_OLD_NAME
import platform.windows.FILE_FLAG_BACKUP_SEMANTICS
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.FILE_LIST_DIRECTORY
import platform.windows.FILE_NOTIFY_CHANGE_ATTRIBUTES
import platform.windows.FILE_NOTIFY_CHANGE_CREATION
import platform.windows.FILE_NOTIFY_CHANGE_DIR_NAME
import platform.windows.FILE_NOTIFY_CHANGE_FILE_NAME
import platform.windows.FILE_NOTIFY_CHANGE_LAST_ACCESS
import platform.windows.FILE_NOTIFY_CHANGE_LAST_WRITE
import platform.windows.FILE_NOTIFY_CHANGE_SECURITY
import platform.windows.FILE_NOTIFY_CHANGE_SIZE
import platform.windows.FILE_NOTIFY_INFORMATION
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GetLastError
import platform.windows.OPEN_EXISTING
import platform.windows.ReadDirectoryChangesW

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
        dwFlagsAndAttributes = FILE_FLAG_BACKUP_SEMANTICS.toUInt() or FILE_FLAG_OVERLAPPED.toUInt(),
        hTemplateFile = null
    ) ?: throw lastErrorToIOException()

    actual fun startWatching() {
        thread(::watch)
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
                }.concatToString().also(::print).let(path::resolve)
                when (event.Action.toInt()) {
                    FILE_ACTION_ADDED -> {
                        print(" created")
                        if (FileChangeEvent.Create in events) {
                            onEvent(FileChangeEvent.Create, path)
                        }
                    }

                    FILE_ACTION_MODIFIED -> {
                        print(" modified")
                        if (FileChangeEvent.Modify in events) {
                            onEvent(FileChangeEvent.Modify, path)
                        }
                    }

                    FILE_ACTION_REMOVED -> {
                        print(" removed")
                        if (FileChangeEvent.Delete in events) {
                            onEvent(FileChangeEvent.Delete, path)
                        }
                    }

                    FILE_ACTION_RENAMED_OLD_NAME -> {
                        print(" renamed from")
                        if (FileChangeEvent.Delete in events) {
                            onEvent(FileChangeEvent.Delete, path)
                        }
                    }

                    FILE_ACTION_RENAMED_NEW_NAME -> {
                        print(" renamed to")
                        if (FileChangeEvent.Create in events) {
                            onEvent(FileChangeEvent.Create, path)
                        }
                    }
                }
                println()
                if (event.NextEntryOffset == 0u) {
                    break
                }
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
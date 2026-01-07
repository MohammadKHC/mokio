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
import platform.windowsx.REPARSE_DATA_BUFFER
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

fun HANDLE.getFileInformation(): BY_HANDLE_FILE_INFORMATION = memScoped {
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
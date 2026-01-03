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
import platform.windows.*

fun FileDescriptor(handle: HANDLE) =
    FileDescriptor(FileDescriptorHandle(handle))

internal actual class FileDescriptorHandle(private val handle: HANDLE) {
    actual fun read(buffer: CPointer<ByteVar>, size: Long): Long = memScoped {
        val bytesRead = alloc<UIntVar>()
        val result = ReadFile(
            hFile = handle,
            lpBuffer = buffer,
            nNumberOfBytesToRead = size.toUInt(),
            lpNumberOfBytesRead = bytesRead.ptr,
            lpOverlapped = null
        )
        if (result == 0 && GetLastError().toInt() == ERROR_BROKEN_PIPE) {
            return 0 // EOF.
        } else result.ensureSuccess()
        return bytesRead.value.toLong()
    }

    actual fun write(buffer: CPointer<ByteVar>, size: Long): Long = memScoped {
        val bytesWritten = alloc<UIntVar>()
        WriteFile(
            hFile = handle,
            lpBuffer = buffer,
            nNumberOfBytesToWrite = size.toUInt(),
            lpNumberOfBytesWritten = bytesWritten.ptr,
            lpOverlapped = null
        ).ensureSuccess()
        if (bytesWritten.value.toLong() < size) {
            throw lastErrorToIOException()
        }
        return bytesWritten.value.toLong()
    }

    actual fun flush() =
        FlushFileBuffers(handle).ensureSuccess()

    actual fun close() =
        CloseHandle(handle).ensureSuccess()
}
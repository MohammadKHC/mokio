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
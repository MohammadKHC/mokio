package com.mohammedkhc.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.convert
import platform.posix.fsync

fun FileDescriptor(fd: Int) =
    FileDescriptor(FileDescriptorHandle(fd))

internal actual class FileDescriptorHandle(private val fd: Int) {
    actual fun read(buffer: CPointer<ByteVar>, size: Long): Long {
        val result = platform.posix.read(fd, buffer, size.convert())
        if (result == -1L) {
            throw errnoToIOException()
        }
        return result
    }

    actual fun write(buffer: CPointer<ByteVar>, size: Long): Long {
        val result = platform.posix.write(fd, buffer, size.convert())
        if (result == -1L || result < size) {
            throw errnoToIOException()
        }
        return result
    }

    actual fun flush() {
        // Not supported, because we don't use fopen/fdopen
        // fsync(fd).ensureSuccess()
    }

    actual fun close() =
        platform.posix.close(fd).ensureSuccess()
}
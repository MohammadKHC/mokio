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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.convert

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
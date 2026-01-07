/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on [okio.FileSource, okio.FileSink]
 * and modified by MohammedKHC.
 */

package com.mohammedkhc.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.Buffer.UnsafeCursor
import okio.Sink
import okio.Source
import okio.Timeout

class FileDescriptor internal constructor(
    private val handle: FileDescriptorHandle,
) : Source, Sink {
    private val unsafeCursor = UnsafeCursor()
    private var closed = false

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        check(!closed) { "closed" }
        val sinkInitialSize = sink.size

        // Request a writable segment in `sink`. We request at least 1024 bytes, unless the request is
        // for smaller than that, in which case we request only that many bytes.
        val cursor = sink.readAndWriteUnsafe(unsafeCursor)
        val addedCapacityCount = cursor.expandBuffer(minByteCount = minOf(byteCount, 1024L).toInt())

        // Now that we have a writable segment, figure out how many bytes to read. This is the smaller
        // of the user's requested byte count, and the segment's writable capacity.
        val attemptCount = minOf(byteCount, addedCapacityCount)

        // Copy bytes from the file to the segment.
        val bytesRead = cursor.data!!.usePinned { pinned ->
            handle.read(pinned.addressOf(cursor.start), attemptCount)
        }

        // Remove new capacity that was added but not used.
        cursor.resizeBuffer(sinkInitialSize + bytesRead)
        cursor.close()

        return if (bytesRead == 0L) -1L else bytesRead
    }

    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }
        check(!closed) { "closed" }

        var byteCount = byteCount
        while (byteCount > 0) {
            // Get the first segment, which we will read a contiguous range of bytes from.
            val cursor = source.readUnsafe(unsafeCursor)
            val segmentReadableByteCount = cursor.next()
            val attemptCount = minOf(byteCount, segmentReadableByteCount.toLong())

            // Copy bytes from that segment into the file.
            val bytesWritten = cursor.data!!.usePinned { pinned ->
                handle.write(pinned.addressOf(cursor.start), attemptCount)
            }

            // Consume the bytes from the segment.
            cursor.close()
            source.skip(bytesWritten)
            byteCount -= bytesWritten
        }
    }

    override fun flush() = handle.flush()
    override fun timeout() = Timeout.NONE
    override fun close() {
        if (closed) return
        closed = true
        handle.close()
    }
}

internal expect class FileDescriptorHandle {
    fun read(buffer: CPointer<ByteVar>, size: Long): Long
    fun write(buffer: CPointer<ByteVar>, size: Long): Long
    fun flush()
    fun close()
}
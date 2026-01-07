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

import com.mohammedkhc.io.metadata.UnixFileMetadata
import com.mohammedkhc.io.metadata.extendedMetadata
import okio.IOException
import kotlin.test.*
import kotlin.time.Clock

class FileMetadataTest {
    @Test
    fun test() = runFileSystemTest { dir ->
        val now = Clock.System.now()
        val path = createBasicFile(dir)
        val metadata = extendedMetadata(path)
        assertTrue(metadata.isRegularFile)
        assertEquals(13, metadata.size)
        val timeRange = now..Clock.System.now()
        assertTimeIn(timeRange, metadata.creationTime)
        assertTimeIn(timeRange, metadata.lastModifiedTime)
        assertTimeIn(timeRange, metadata.lastAccessTime)
    }

    @Test
    fun symlinkTest() = runFileSystemTest { dir ->
        val regularPath = createBasicFile(dir)
        val symlink = dir / "symlink.txt"
        createSymbolicLink(symlink, regularPath)

        val regularMetadata = extendedMetadata(regularPath)
        assertTrue(regularMetadata.isRegularFile)
        assertFalse(regularMetadata.isSymbolicLink)
        assertFalse(regularMetadata.isDirectory)
        assertFalse(regularMetadata.isOther)
        assertEquals(13, regularMetadata.size)
        assertFailsWith<IOException> {
            readSymbolicLink(regularPath)
        }

        assertEquals(
            regularMetadata,
            extendedMetadata(symlink, followLinks = true)
        )

        val symlinkMetadata = extendedMetadata(symlink, followLinks = false)
        assertTrue(symlinkMetadata.isSymbolicLink)
        assertFalse(symlinkMetadata.isRegularFile)
        assertFalse(symlinkMetadata.isDirectory)
        assertFalse(symlinkMetadata.isOther)

        assertEquals(
            if (symlinkMetadata is UnixFileMetadata) {
                regularPath.toString().length.toLong()
            } else 0,
            symlinkMetadata.size
        )

        assertEquals(regularPath, readSymbolicLink(symlink))
    }
}
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

import okio.IOException
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

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
            if (symlinkMetadata is FileMetadata.Unix) {
                regularPath.toString().length.toLong()
            } else 0,
            symlinkMetadata.size
        )

        assertEquals(regularPath, readSymbolicLink(symlink))
    }

    @Test
    fun setCreationTimeTest() = runFileSystemTest(false) { dir ->
        if (!isWindows && !isMacOs)
            return@runFileSystemTest

        val now = Clock.System.now()
        val path = createBasicFile(dir)
        val time = Instant.parse("2010-10-10T10:10:10Z")

        setMetadata(
            path,
            true,
            FileMetadata.Attribute.Time.Creation(time)
        )
        val metadata = extendedMetadata(path)
        assertEquals(time, metadata.creationTime)

        val timeRange = now..Clock.System.now()
        assertTimeIn(timeRange, metadata.lastModifiedTime)
        assertTimeIn(timeRange, metadata.lastAccessTime)
    }

    @Test
    fun setLastModifiedTimeTest() = runFileSystemTest(false) { dir ->
        val now = Clock.System.now()
        val path = createBasicFile(dir)
        val time = Instant.parse("2009-09-09T09:09:09Z")

        setMetadata(
            path,
            true,
            FileMetadata.Attribute.Time.LastModified(time)
        )
        val metadata = extendedMetadata(path)
        assertEquals(time, metadata.lastModifiedTime)

        val timeRange = now..Clock.System.now()
        // Verify creation time remains unchanged, accounting for
        // platforms where it may be aliased to last modified time.
        if (metadata.creationTime != time) {
            assertTimeIn(timeRange, metadata.creationTime)
        }
        assertTimeIn(timeRange, metadata.lastAccessTime)
    }

    @Test
    fun setLastAccessTimeTest() = runFileSystemTest(false) { dir ->
        val now = Clock.System.now()
        val path = createBasicFile(dir)
        val time = Instant.parse("2008-08-08T08:08:08Z")

        try {
            setMetadata(
                path,
                true,
                FileMetadata.Attribute.Time.LastAccess(time)
            )
        } catch (_: UnsupportedOperationException) {
            assertFalse(isAndroidSdkAtLeast(26))
            return@runFileSystemTest
        }
        val metadata = extendedMetadata(path)
        assertEquals(time, metadata.lastAccessTime)

        val timeRange = now..Clock.System.now()
        assertTimeIn(timeRange, metadata.creationTime)
        assertTimeIn(timeRange, metadata.lastModifiedTime)
    }

    @Test
    fun setUnixFileModeTest() = runFileSystemTest(false) { dir ->
        val path = createBasicFile(dir)
        if (extendedMetadata(path) !is FileMetadata.Unix)
            return@runFileSystemTest

        val mode = FileMode(FileMode.Type.RegularFile, setOf(FileMode.Permission.OwnerExecute))
        setMetadata(
            path,
            true,
            FileMetadata.Attribute.Unix.FileMode(mode)
        )
        val metadata = extendedMetadata(path)
        assertIs<FileMetadata.Unix>(metadata)
        assertEquals(mode, metadata.mode)
    }

    @Test
    fun setUnixOwnerTest() = runFileSystemTest(false) { dir ->
        val path = createBasicFile(dir)
        if (extendedMetadata(path) !is FileMetadata.Unix)
            return@runFileSystemTest

        // We expect this to fail with IOException with "Operation not permitted" as the message.
        // This is because it's unlikely that this owner (uid=12346u, gid=64321u) does really exist.
        assertFailsWith<IOException> {
            setMetadata(
                path,
                true,
                FileMetadata.Attribute.Unix.Owner(12346u, 64321u)
            )
        }
    }

    @Test
    fun setWindowsAttributesTest() = runFileSystemTest(false) { dir ->
        val path = createBasicFile(dir)
        if (extendedMetadata(path) !is FileMetadata.Windows)
            return@runFileSystemTest

        setMetadata(
            path,
            true,
            FileMetadata.Attribute.Windows.ReadOnly(true),
            FileMetadata.Attribute.Windows.Archive(true)
        )
        val metadata = extendedMetadata(path)
        assertIs<FileMetadata.Windows>(metadata)
        assertTrue(metadata.isReadOnly)
        assertTrue(metadata.isArchive)
    }
}
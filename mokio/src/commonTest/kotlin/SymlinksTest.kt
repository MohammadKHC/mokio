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

import com.mohammedkhc.io.metadata.extendedMetadata
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymlinksTest {
    @Test
    fun test() = runFileSystemTest(false) { dir ->
        createBasicFile(dir)

        val symlink = dir / "symlink.txt"
        val symlinkTarget = "basic.txt".toPath()

        createSymbolicLink(symlink, symlinkTarget)
        val symlinkMetadata = extendedMetadata(symlink, followLinks = false)
        assertTrue(symlinkMetadata.isSymbolicLink)
        assertEquals(symlinkTarget, readSymbolicLink(symlink))
        val regularContent = read(symlink.parent!! / symlinkTarget) {
            readUtf8()
        }
        assertEquals("Hello, world!", regularContent)
    }

    @Test
    fun exactTest() = runFileSystemTest(false) { dir ->
        createBasicFile(dir)

        val symlink = dir / "symlink.txt"
        val symlinkTarget = "././basic.txt"
            .replace("/", Path.DIRECTORY_SEPARATOR)

        createSymbolicLinkExact(symlink, symlinkTarget)
        val symlinkMetadata = extendedMetadata(symlink, followLinks = false)
        assertTrue(symlinkMetadata.isSymbolicLink)
        assertEquals(symlinkTarget, readSymbolicLinkExact(symlink))
        val regularContent = read(symlink.parent!! / symlinkTarget) {
            readUtf8()
        }
        assertEquals("Hello, world!", regularContent)
    }
}
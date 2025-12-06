package com.mohammedkhc.io

import okio.IOException
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatTest {
    @Test
    fun test() = runFileSystemTest { dir ->
        val path = dir / "basic.txt".toPath()
        write(path) {
            writeUtf8("Hello, world!")
        }
        val stat = stat(path)
        assertEquals(FileMode.Type.RegularFile, stat.mode.type)
        assertEquals(13, stat.size)
    }

    @Test
    fun symlinkTest() = runFileSystemTest { dir ->
        val regularPath = dir / "basic.txt".toPath()
        write(regularPath) {
            writeUtf8("Hello, world!")
        }
        val symlink = dir / "symlink.txt".toPath()
        createSymbolicLink(symlink, regularPath.toString())
        val regularStat = stat(regularPath)
        assertEquals(FileMode.Type.RegularFile, regularStat.mode.type)
        assertEquals(13, regularStat.size)
        assertFailsWith<IOException> {
            readSymbolicLink(regularPath)
        }
        val symlinkStat = stat(symlink, followSymlinks = false)
        assertEquals(FileMode.Type.SymbolicLink, symlinkStat.mode.type)
        assertEquals(regularPath.toString().length.toLong(), symlinkStat.size)
        assertEquals(regularPath.toString(), readSymbolicLink(symlink))
    }
}
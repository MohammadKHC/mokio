package com.mohammedkhc.io

import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class SymlinksTest {
    @Test
    fun test() = runFileSystemTest(false) { dir ->
        val regularPath = dir / "basic.txt".toPath()
        write(regularPath) {
            writeUtf8("Hello, world!")
        }

        val symlink = dir / "symlink.txt".toPath()
        val symlinkTarget =
            if (Path.DIRECTORY_SEPARATOR == "\\") ".\\.\\basic.txt"
            else "././basic.txt"

        createSymbolicLink(symlink, symlinkTarget)
        val symlinkStat = stat(symlink, followSymlinks = false)
        assertEquals(FileMode.Type.SymbolicLink, symlinkStat.mode.type)
        assertEquals(symlinkTarget, readSymbolicLink(symlink))
        val regularContent = read(symlink.parent!! / readSymbolicLink(symlink)) {
            readUtf8()
        }
        assertEquals("Hello, world!", regularContent)
    }
}
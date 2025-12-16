package com.mohammedkhc.io

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test

class FileWatcherTest {
    @Test
    fun basicTest() {
        val dir = createTempDirectory()
        FileWatcher(dir, recursive = true) { event, path ->
            println("$event, $path")
        }.startWatching()
        FileSystem.SYSTEM.createDirectory(dir / "sub")
        FileSystem.SYSTEM.write(dir / "sub/test".toPath()) {
            writeUtf8("hello")
        }
        FileSystem.SYSTEM.createDirectory(dir / "sub/other".toPath())
        FileSystem.SYSTEM.write(dir / "sub/other/dtest") {
            writeUtf8("hello")
        }

        FileSystem.SYSTEM.atomicMove(dir / "sub", dir / "newsub")
        Process(listOf("sleep", "2")).waitFor()
        FileSystem.SYSTEM.write(dir / "newsub/other/dtest") {
            writeUtf8("hello")
        }
        Process(listOf("sleep", "2")).waitFor()
    }
}
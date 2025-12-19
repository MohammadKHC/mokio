package com.mohammedkhc.io

import okio.FileSystem
import okio.Path
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
        initMyDirectory(dir)

        Process(listOf("sleep", "2")).waitFor()
        println("Moving myDirectory to myNewDirectory")
        FileSystem.SYSTEM.atomicMove(dir / "myDirectory", dir / "myNewDirectory")

        Process(listOf("sleep", "2")).waitFor()
        println("Writing to myNestedFile.")
        FileSystem.SYSTEM.write(dir / "myNewDirectory/mySubDirectory/myNestedFile") {
            writeUtf8("Bye!")
        }

        Process(listOf("sleep", "2")).waitFor()
        println("Updating myFile mode to read only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerRead, FileMode.Permission.OthersRead, FileMode.Permission.GroupRead)))

        Process(listOf("sleep", "2")).waitFor()
        println("Updating myFile mode to write only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerWrite, FileMode.Permission.OthersWrite, FileMode.Permission.GroupWrite)))

        Process(listOf("sleep", "2")).waitFor()
        println("Updating myFile mode to execute only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerExecute, FileMode.Permission.OthersExecute, FileMode.Permission.GroupExecute)))

        Process(listOf("sleep", "2")).waitFor()
        println("Updating myFile mode to all.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = FileMode.Permission.entries.toSet()))

        Process(listOf("sleep", "5")).waitFor()
    }

    private fun initMyDirectory(parent: Path) {
        println("Creating myDirectory and it's children.")
        FileSystem.SYSTEM.createDirectory(parent / "myDirectory")
        FileSystem.SYSTEM.write(parent / "myDirectory/myFile") {
            writeUtf8("Hello!")
        }
        FileSystem.SYSTEM.createDirectory(parent / "myDirectory/mySubDirectory")
        FileSystem.SYSTEM.write(parent / "myDirectory/mySubDirectory/myNestedFile") {
            writeUtf8("Hello, nested!")
        }
    }
}
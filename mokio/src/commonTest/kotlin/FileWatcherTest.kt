package com.mohammedkhc.io

import kotlinx.datetime.LocalDate.Formats.ISO_BASIC
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class FileWatcherTest {
    @Test
    fun basicTest() {
        val dir = createTempDirectory()
        FileWatcher(dir, recursive = true) { event, path ->
            println("$event, $path")
        }.startWatching()
        sleep(5)
        initMyDirectory(dir)

        sleep(2)
        println("Moving myDirectory to myNewDirectory")
        FileSystem.SYSTEM.atomicMove(dir / "myDirectory", dir / "myNewDirectory")

        sleep(2)
        println("Writing to myNestedFile.")
        FileSystem.SYSTEM.write(dir / "myNewDirectory/mySubDirectory/myNestedFile") {
            writeUtf8("Bye!")
        }

        sleep(2)
        println("Updating myFile mode to read only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerRead, FileMode.Permission.OthersRead, FileMode.Permission.GroupRead)))

        sleep(2)
        println("Updating myFile mode to write only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerWrite, FileMode.Permission.OthersWrite, FileMode.Permission.GroupWrite)))

        sleep(2)
        println("Updating myFile mode to execute only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerExecute, FileMode.Permission.OthersExecute, FileMode.Permission.GroupExecute)))

        sleep(2)
        println("Updating myFile mode to all.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = FileMode.Permission.entries.toSet()))

        sleep(2)
        val time = Instant.fromEpochSeconds(Clock.System.now().epochSeconds - 10000)
        println("Updating myFile last modified time to be $time.")
        setLastModifiedTime(dir / "myNewDirectory/myFile", time)

        sleep(2)
        println("Updating myFile last access time to be $time.")
        setLastAccessTime(dir / "myNewDirectory/myFile", time)

        sleep(2)
        println("Updating dir last modified time to be $time.")
        setLastModifiedTime(dir, time)

        sleep(5)
    }

    @Test
    fun basicTestCopy() {
        println("not recursive")
        val dir = createTempDirectory()
        FileWatcher(dir, recursive = false) { event, path ->
            println("$event, $path")
        }.startWatching()
        sleep(5)
        initMyDirectory(dir)

        sleep(2)
        println("Moving myDirectory to myNewDirectory")
        FileSystem.SYSTEM.atomicMove(dir / "myDirectory", dir / "myNewDirectory")

        sleep(2)
        println("Writing to myNestedFile.")
        FileSystem.SYSTEM.write(dir / "myNewDirectory/mySubDirectory/myNestedFile") {
            writeUtf8("Bye!")
        }

        sleep(2)
        println("Updating myFile mode to read only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerRead, FileMode.Permission.OthersRead, FileMode.Permission.GroupRead)))

        sleep(2)
        println("Updating myFile mode to write only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerWrite, FileMode.Permission.OthersWrite, FileMode.Permission.GroupWrite)))

        sleep(2)
        println("Updating myFile mode to execute only.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = setOf(
            FileMode.Permission.OwnerExecute, FileMode.Permission.OthersExecute, FileMode.Permission.GroupExecute)))

        sleep(2)
        println("Updating myFile mode to all.")
        FileSystem.SYSTEM.setFileMode(dir / "myNewDirectory/myFile", FileMode(FileMode.Type.RegularFile, permissions = FileMode.Permission.entries.toSet()))

        sleep(2)
        val time = Instant.fromEpochSeconds(Clock.System.now().epochSeconds - 10000)
        println("Updating myFile last modified time to be $time.")
        setLastModifiedTime(dir / "myNewDirectory/myFile", time)

        sleep(2)
        println("Updating myFile last access time to be $time.")
        setLastAccessTime(dir / "myNewDirectory/myFile", time)

        sleep(2)
        println("Writing to myNewFile.")
        FileSystem.SYSTEM.write(dir / "myNewFile") {
            writeUtf8("Bye!")
        }

        sleep(5)
    }

    private fun initMyDirectory(parent: Path) {
        println("Creating myDirectory and it's children.")
        FileSystem.SYSTEM.createDirectory(parent / "myDirectory")
        sleep(2)
        FileSystem.SYSTEM.write(parent / "myDirectory/myFile") {
            writeUtf8("Hello!")
        }
        FileSystem.SYSTEM.createDirectory(parent / "myDirectory/mySubDirectory")
        sleep(2)
        FileSystem.SYSTEM.write(parent / "myDirectory/mySubDirectory/myNestedFile") {
            writeUtf8("Hello, nested!")
        }
    }

    private fun setLastModifiedTime(path: Path, time: Instant) {
        Process(listOf("touch", path.toString(), "-m", "-t", time.format(DateTimeComponents.Format {
            date(ISO_BASIC)
            hour()
            minute()
            optional { char('.'); second() }
        }))).waitFor()
    }

    private fun setLastAccessTime(path: Path, time: Instant) {
        Process(listOf("touch", path.toString(), "-a", "-t", time.format(DateTimeComponents.Format {
            date(ISO_BASIC)
            hour()
            minute()
            optional { char('.'); second() }
        }))).waitFor()
    }
}
package com.mohammedkhc.io

import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random

internal val isUnixLikeOs get() = Path.DIRECTORY_SEPARATOR == "/"

internal fun createTempDirectory() =
    FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve(
        Random.nextBytes(20).toHexString()
    ).also(FileSystem.SYSTEM::createDirectory)

internal fun runFileSystemTest(
    supportsFakeFileSystem: Boolean = true,
    test: FileSystem.(directory: Path) -> Unit
) {
    FileSystem.SYSTEM.test(createTempDirectory())
    if (!supportsFakeFileSystem)
        return

    FakeFileSystem().apply {
        emulateUnix()
        test(workingDirectory)
    }
    FakeFileSystem().apply {
        emulateWindows()
        // Allow symlinks as it's supported on our mingwX64 implementation.
        allowSymlinks = true
        test(workingDirectory)
    }
}

internal fun sleep(seconds: Int) {
    Process(listOf("sleep", seconds.toString())).waitFor()
}
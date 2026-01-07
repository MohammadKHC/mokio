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

import okio.*
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.time.Instant

internal fun createTempDirectory() =
    FileSystem.SYSTEM.canonicalize(
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    ).resolve(Random.nextBytes(20).toHexString())
        .also(FileSystem.SYSTEM::createDirectory)

internal fun FileSystem.createBasicFile(dir: Path): Path {
    val path = dir / "basic.txt"
    write(path) {
        writeUtf8("Hello, world!")
    }
    return path
}

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

internal fun assertTimeIn(range: ClosedRange<Instant>, time: Instant) {
    val start = Instant.fromEpochSeconds(range.start.epochSeconds - 1)
    val end = Instant.fromEpochSeconds(range.endInclusive.epochSeconds + 1)
    assertContains(start..end, time)
}

// HACKS To get info about the current os.
internal val isUnix get() = Path.DIRECTORY_SEPARATOR == "/"
internal val isWindows get() = Path.DIRECTORY_SEPARATOR == "\\"

internal val isMacOs by lazy {
    if (!isUnix) return@lazy false
    try {
        Process(listOf("uname"))
            .outputSource.buffer()
            .use(BufferedSource::readUtf8)
            .trim() == "Darwin"
    } catch (_: IOException) {
        false
    }
}

private val androidCurrentSdkVersion by lazy {
    if (!isUnix) return@lazy null
    try {
        Process(listOf("getprop", "ro.build.version.sdk"))
            .outputSource.buffer()
            .use(BufferedSource::readUtf8)
            .trim().toIntOrNull()
    } catch (_: IOException) {
        null
    }
}

fun isAndroidSdkAtLeast(sdk: Int): Boolean {
    val currentSdk = androidCurrentSdkVersion ?: return true
    return currentSdk >= sdk
}
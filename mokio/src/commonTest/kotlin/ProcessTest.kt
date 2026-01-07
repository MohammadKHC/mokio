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

import okio.FileSystem
import okio.SYSTEM
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessTest {
    @Test
    fun inputTest() {
        val process = Process(
            if (isWindows) shellFile("@echo off\r\necho Enter your name\r\nset /p name=\r\necho Hello, %name%")
            else shellCommand($$"echo Enter your name && read name && echo Hello, $name")
        )
        val input = process.inputSink.buffer()
        val output = process.outputSource.buffer()
        assertEquals("Enter your name", output.readUtf8LineStrict())
        input.writeUtf8("MohammedKHC")
        input.writeByte('\n'.code)
        input.flush()
        assertEquals("Hello, MohammedKHC", output.readUtf8LineStrict())
        assertTrue(output.exhausted())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun outputTest() {
        val process = Process(shellCommand("echo Hello, world!"))
        val output = process.outputSource.buffer()
        assertEquals("Hello, world!", output.readUtf8().trimEnd())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun errorTest() {
        val process = Process(shellCommand("echo This is an error message.>&2"))
        val error = process.errorSource.buffer()
        assertEquals("This is an error message.", error.readUtf8().trimEnd())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun directoryTest() {
        val tempPath = createTempDirectory()
            .let(FileSystem.SYSTEM::canonicalize)
        val process = Process(
            shellCommand(if (isWindows) "cd" else "pwd"),
            directory = tempPath
        )
        val output = process.outputSource.buffer()
        assertEquals(tempPath.toString(), output.readUtf8().trimEnd())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun environmentTest() {
        val process = Process(
            shellCommand(
                if (isWindows) "echo %RANDOM_ENV%"
                else $$"echo $RANDOM_ENV"
            ),
            environment = mapOf("RANDOM_ENV" to "VALID_RANDOM_ENV")
        )
        val output = process.outputSource.buffer()
        assertContains(output.readUtf8().trimEnd(), "VALID_RANDOM_ENV")
        assertEquals(0, process.waitFor())
    }

    @Test
    fun redirectErrorTest() {
        val process = Process(
            shellCommand("echo This is an error message.>&2"),
            redirectErrorSource = true
        )
        val output = process.outputSource.buffer()
        val error = process.errorSource.buffer()
        assertEquals("This is an error message.", output.readUtf8().trimEnd())
        assertTrue(error.exhausted())
        assertEquals(0, process.waitFor())
    }

    private fun shellCommand(command: String) =
        if (isWindows) listOf("cmd.exe", "/c", command)
        else listOf("sh", "-c", command)

    private fun shellFile(fileContent: String): List<String> {
        val file = createTempDirectory() / "file.${if (isWindows) "bat" else "sh"}"
        FileSystem.SYSTEM.write(file) {
            writeUtf8(fileContent)
        }
        return if (isWindows) listOf("cmd.exe", "/c", file.toString())
        else listOf("sh", file.toString())
    }
}
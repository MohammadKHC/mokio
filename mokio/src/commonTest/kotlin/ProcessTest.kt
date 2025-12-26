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
            if (isUnixLikeOs) shellCommand($$"echo Enter your name && read name && echo Hello, $name")
            else shellFile("@echo off\r\necho Enter your name\r\nset /p name=\r\necho Hello, %name%")
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
            shellCommand(if (isUnixLikeOs) "pwd" else "cd"),
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
                if (isUnixLikeOs) $$"echo $RANDOM_ENV"
                else "echo %RANDOM_ENV%"
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
        if (isUnixLikeOs) listOf("sh", "-c", command)
        else listOf("cmd.exe", "/c", command)

    private fun shellFile(fileContent: String): List<String> {
        val file = createTempDirectory() / "file.${if (isUnixLikeOs) "sh" else "bat"}"
        FileSystem.SYSTEM.write(file) {
            writeUtf8(fileContent)
        }
        return if (isUnixLikeOs) listOf("sh", file.toString())
        else listOf("cmd.exe", "/c", file.toString())
    }
}
package com.mohammedkhc.io

import okio.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessTest {
    @Test
    fun inputTest() {
        val process = Process(shellCommand("echo Hello, world!"))
        val input = process.inputSource.buffer()
        assertEquals("Hello, world!", input.readUtf8().trimEnd())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun outputTest() {
        val process = Process(
            if (isUnixLikeOs) shellCommand($$"echo Enter your name && read name && echo Hello, $name")
            else shellFile("@echo off\r\necho Enter your name\r\nset /p name=\r\necho Hello, %name%")
        )
        val input = process.inputSource.buffer()
        val output = process.outputSink.buffer()
        assertEquals("Enter your name", input.readUtf8LineStrict())
        output.writeUtf8("MohammedKHC")
        output.writeByte('\n'.code)
        output.flush()
        assertEquals("Hello, MohammedKHC", input.readUtf8LineStrict())
        assertTrue(input.exhausted())
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
        val input = process.inputSource.buffer()
        assertEquals(tempPath.toString(), input.readUtf8().trimEnd())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun environmentTest() {
        error(buildString {
            runCatching {
                Process(
                    shellCommand($$"echo $PATH"),
                    environment = mapOf("RANDOM_ENV" to "VALID_RANDOM_ENV")
                ).inputSource.buffer().readUtf8().run { append("one env: $this") }
            }
            runCatching {
                Process(
                    shellCommand($$"echo $PATH"),
                    //environment = mapOf("RANDOM_ENV" to "VALID_RANDOM_ENV")
                ).inputSource.buffer().readUtf8().run { append("one no: $this") }
            }
            runCatching {
                Process(
                    shellCommand("printenv"),
                    environment = mapOf("RANDOM_ENV" to "VALID_RANDOM_ENV")
                ).inputSource.buffer().readUtf8().run { append("printenv env: $this") }
            }
            runCatching {
                Process(
                    shellCommand("printenv")
                ).inputSource.buffer().readUtf8().run { append("printenv no: $this") }
            }
        })
        val process = Process(
            shellCommand(
                if (isUnixLikeOs) $$"echo $RANDOM_ENV"
                else "echo %RANDOM_ENV%"
            ),
            environment = mapOf("RANDOM_ENV" to "VALID_RANDOM_ENV")
        )
        val input = process.inputSource.buffer()
        assertContains(input.readUtf8().trimEnd(), "VALID_RANDOM_ENV")
        assertEquals(0, process.waitFor())
    }

    @Test
    fun redirectErrorTest() {
        val process = Process(
            shellCommand("echo This is an error message.>&2"),
            redirectErrorToInput = true
        )
        val input = process.inputSource.buffer()
        val error = process.errorSource.buffer()
        assertEquals("This is an error message.", input.readUtf8().trimEnd())
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
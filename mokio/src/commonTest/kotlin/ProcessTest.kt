package com.mohammedkhc.io

import okio.BufferedSource
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessTest {
    @Test
    fun outputTest() {
        val process = Process(shellCommand("echo Hello, world!"))
        val output = process.inputSource.buffer().use(BufferedSource::readUtf8Line)
        assertEquals("Hello, wodrld!", output)
    }

    private fun shellCommand(command: String) =
        if (isUnixLikeOs) listOf("sh", "-c", command)
        else listOf("cmd.exe", "/c", command)
}
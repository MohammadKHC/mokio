package com.mohammedkhc.io

import okio.Path
import okio.sink
import okio.source

actual class Process actual constructor(
    command: List<String>,
    directory: Path?,
    environment: Map<String, String>?,
    redirectErrorToInput: Boolean
) {
    private val process = ProcessBuilder(command).apply {
        directory?.toFile()?.let(::directory)
        if (environment != null) {
            environment().clear()
            environment() += environment
        }
        redirectErrorStream(redirectErrorToInput)
    }.start()

    actual val pid get() = process.pid().toUInt()
    actual val isAlive get() = process.isAlive
    actual val inputSource = process.inputStream.source()
    actual val outputSink = process.outputStream.sink()
    actual val errorSource = process.errorStream.source()
    actual fun wait() = process.waitFor()

    actual fun destroy(force: Boolean) {
        if (force) process.destroyForcibly()
        else process.destroy()
    }
}
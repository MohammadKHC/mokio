package com.mohammedkhc.io

import okio.Path
import okio.sink
import okio.source

actual class Process actual constructor(
    command: List<String>,
    directory: Path?,
    environment: Map<String, String>?,
    redirectErrorSource: Boolean
) {
    private val process = ProcessBuilder(command).apply {
        directory?.toFile()?.let(::directory)
        if (environment != null) {
            environment().clear()
            environment() += environment
        }
        redirectErrorStream(redirectErrorSource)
    }.start()

    actual val pid get() = process.pid().toUInt()
    actual val isAlive get() = process.isAlive
    actual val inputSink = process.outputStream.sink()
    actual val outputSource = process.inputStream.source()
    actual val errorSource = process.errorStream.source()
    actual fun waitFor() = process.waitFor()

    actual fun destroy(force: Boolean) {
        if (force) process.destroyForcibly()
        else process.destroy()
    }
}
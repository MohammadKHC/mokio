package com.mohammedkhc.io

import okio.Path
import okio.Sink
import okio.Source

expect class Process(
    command: List<String>,
    directory: Path? = null,
    environment: Map<String, String>? = null,
    redirectErrorSource: Boolean = false
) {
    val pid: UInt
    val isAlive: Boolean
    val inputSink: Sink
    val outputSource: Source
    val errorSource: Source
    fun waitFor(): Int
    fun destroy(force: Boolean = false)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.closeIO() {
    runCatching(inputSink::close)
    runCatching(outputSource::close)
    runCatching(errorSource::close)
}
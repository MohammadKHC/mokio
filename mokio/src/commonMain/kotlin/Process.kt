package com.mohammedkhc.io

import okio.Path
import okio.Sink
import okio.Source

expect class Process(
    command: List<String>,
    directory: Path? = null,
    environment: Map<String, String>? = null,
    redirectErrorToInput: Boolean = false
) {
    val pid: UInt
    val isAlive: Boolean
    val inputSource: Source
    val outputSink: Sink
    val errorSource: Source
    fun waitFor(): Int
    fun destroy(force: Boolean = false)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Process.closeIO() {
    runCatching(inputSource::close)
    runCatching(outputSink::close)
    runCatching(errorSource::close)
}
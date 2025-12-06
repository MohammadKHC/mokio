package com.mohammedkhc.io

import kotlinx.cinterop.toKString
import okio.FileNotFoundException
import okio.IOException
import platform.posix.ENOENT
import platform.posix.errno
import platform.posix.strerror

internal fun errnoToIOException(): IOException {
    val errno = errno
    val errorMessage = strerror(errno)?.toKString()
        ?: "errno: $errno"
    return when (errno) {
        ENOENT -> FileNotFoundException(errorMessage)
        else -> IOException(errorMessage)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Int.ensureSuccess() {
    if (this != 0) {
        throw errnoToIOException()
    }
}
package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.FileNotFoundException
import okio.IOException
import platform.windows.*

internal fun lastErrorToIOException(): IOException {
    val error = GetLastError()
    val message = memScoped {
        val buffer = alloc<CPointerVar<UShortVar>>()
        FormatMessageW(
            dwFlags = FORMAT_MESSAGE_ALLOCATE_BUFFER.toUInt() or FORMAT_MESSAGE_FROM_SYSTEM.toUInt() or FORMAT_MESSAGE_IGNORE_INSERTS.toUInt(),
            lpSource = null,
            dwMessageId = error,
            dwLanguageId = 0u,
            lpBuffer = buffer.ptr.reinterpret(),
            nSize = 0u,
            Arguments = null
        )
        buffer.value?.toKString() ?: "Error: $error"
    }
    return when (error.toInt()) {
        ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> FileNotFoundException(message)
        else -> IOException(message)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Int.ensureSuccess() {
    if (this == 0) {
        throw lastErrorToIOException()
    }
}
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

@Suppress("NOTHING_TO_INLINE")
internal inline fun Int.checkNotNegativeOne(): Int {
    if (this == -1) {
        throw errnoToIOException()
    }
    return this
}
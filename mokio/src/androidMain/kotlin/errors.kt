package com.mohammedkhc.io

import android.system.ErrnoException
import android.system.OsConstants
import okio.IOException
import java.io.FileNotFoundException

internal fun ErrnoException.toIOException(): IOException = when (errno) {
    OsConstants.ENOENT -> FileNotFoundException(message)
        .also { it.initCause(this) }

    else -> IOException(message, this)
}
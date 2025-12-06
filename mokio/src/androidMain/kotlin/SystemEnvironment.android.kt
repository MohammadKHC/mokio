package com.mohammedkhc.io

import android.system.ErrnoException
import android.system.Os

actual object SystemEnvironment {
    actual operator fun get(name: String): String? =
        Os.getenv(name)

    actual operator fun set(name: String, value: String) = try {
        Os.setenv(name, value, true)
    } catch (e: ErrnoException) {
        throw e.toIOException()
    }

    actual operator fun minusAssign(name: String) = try {
        Os.unsetenv(name)
    } catch (e: ErrnoException) {
        throw e.toIOException()
    }
}
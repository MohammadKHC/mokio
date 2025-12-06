package com.mohammedkhc.io

import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv

actual object SystemEnvironment {
    actual operator fun get(name: String): String? =
        getenv(name)?.toKString()

    actual operator fun set(name: String, value: String) =
        setenv(name, value, 1).ensureSuccess()

    actual operator fun minusAssign(name: String) =
        unsetenv(name).ensureSuccess()
}
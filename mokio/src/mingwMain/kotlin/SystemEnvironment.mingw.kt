package com.mohammedkhc.io

import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.GetEnvironmentVariableW
import platform.windows.SetEnvironmentVariableW

actual object SystemEnvironment {
    actual operator fun get(name: String): String? {
        val size = GetEnvironmentVariableW(name, null, 0u)
        if (size == 0u) {
            return null
        }
        memScoped {
            val value = allocArray<UShortVar>(size.toLong())
            if (GetEnvironmentVariableW(name, value, size) == 0u) {
                return null
            }
            return value.toKString()
        }
    }

    actual operator fun set(name: String, value: String) {
        SetEnvironmentVariableW(name, value)
            .ensureSuccess()
    }

    actual operator fun minusAssign(name: String) {
        SetEnvironmentVariableW(name, null)
            .ensureSuccess()
    }
}
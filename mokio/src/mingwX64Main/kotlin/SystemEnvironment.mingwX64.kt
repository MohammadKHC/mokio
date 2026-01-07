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
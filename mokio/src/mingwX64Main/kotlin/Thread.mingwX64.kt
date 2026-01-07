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

import kotlinx.cinterop.*
import platform.windows.*

actual fun thread(name: String?, block: () -> Unit): Thread {
    val handle = CreateThread(
        lpThreadAttributes = null,
        dwStackSize = 0u,
        lpStartAddress = staticCFunction { ptr ->
            val ref = ptr!!.asStableRef<() -> Unit>()
            try {
                ref.get().invoke()
            } finally {
                ref.dispose()
            }
            0u
        },
        lpParameter = StableRef.create(block).asCPointer(),
        dwCreationFlags = 0u,
        lpThreadId = null
    ) ?: throw lastErrorToIOException()
    if (name != null) {
        // SetThreadDescription is only available on Windows 10 1607 and later.
        GetProcAddress(
            GetModuleHandleW("Kernel32.dll"),
            "SetThreadDescription"
        )?.reinterpret<CFunction<Function2<HANDLE, PCWSTR, HRESULT>>>()?.let {
            memScoped { it(handle, name.wcstr.ptr) }
        }
    }
    return Thread(handle)
}

actual class Thread(private val handle: HANDLE) {
    actual fun join() {
        WaitForSingleObject(handle, INFINITE)
    }
}
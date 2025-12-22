package com.mohammedkhc.io

import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import platform.windows.CreateThread
import platform.windows.HANDLE
import platform.windows.INFINITE
import platform.windows.WaitForSingleObject

internal fun thread(
    block: () -> Unit
): Thread {
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
    return Thread(handle)
}

internal value class Thread(private val handle: HANDLE) {
    fun join() {
        WaitForSingleObject(handle, INFINITE)
    }
}
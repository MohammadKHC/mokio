package com.mohammedkhc.io

import kotlinx.cinterop.*
import platform.linux.PR_SET_NAME
import platform.linux.prctl
import platform.posix.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

internal fun thread(
    name: String? = null,
    block: () -> Unit
): Thread = memScoped {
    val thread = alloc<pthread_tVar>()
    pthread_create(
        thread.ptr,
        null,
        staticCFunction { ptr ->
            val ref = ptr!!.asStableRef<Pair<String?, () -> Unit>>()
            val (name, block) = ref.get()
            if (name != null) {
                prctl(PR_SET_NAME, name)
            }
            try {
                block()
            } finally {
                ref.dispose()
            }
            null
        },
        StableRef.create(name to block).asCPointer()
    ).ensureSuccess()
    Thread(thread.value)
}

internal value class Thread(val id: pthread_t) {
    fun join() {
        pthread_join(id, null)
            .ensureSuccess()
    }
}

internal class Mutex {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().apply {
        pthread_mutex_init(ptr, null)
            .ensureSuccess()
    }
    @Suppress("unused")
    @OptIn(ExperimentalNativeApi::class)
    private val cleaner = createCleaner(mutex) {
        pthread_mutex_destroy(it.ptr)
        nativeHeap.free(it)
    }

    @OptIn(ExperimentalContracts::class)
    inline fun <T> withLock(action: () -> T): T {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }
        try {
            pthread_mutex_lock(mutex.ptr)
            return action()
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }
}
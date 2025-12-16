package com.mohammedkhc.io

import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.linux.PR_SET_NAME
import platform.linux.prctl
import platform.posix.SA_INTERRUPT
import platform.posix.pthread_create
import platform.posix.pthread_join
import platform.posix.pthread_kill
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_t
import platform.posix.pthread_tVar
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
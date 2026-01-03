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
import platform.linux.PR_SET_NAME
import platform.linux.prctl
import platform.posix.pthread_create
import platform.posix.pthread_join
import platform.posix.pthread_t
import platform.posix.pthread_tVar

actual fun thread(name: String?, block: () -> Unit): Thread = memScoped {
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

actual class Thread(private val id: pthread_t) {
    actual fun join() = pthread_join(id, null)
        .ensureSuccess()
}
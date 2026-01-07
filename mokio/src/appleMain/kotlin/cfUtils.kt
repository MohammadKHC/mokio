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
import platform.CoreFoundation.*

internal fun String.toCFStringRef(): CFStringRef? =
    CFStringCreateWithCString(
        null,
        this,
        kCFStringEncodingUTF8
    )

internal fun CFStringRef.toKString(): String = memScoped {
    val size = CFStringGetMaximumSizeForEncoding(
        CFStringGetLength(this@toKString),
        kCFStringEncodingUTF8
    ) + 1

    allocArray<ByteVar>(size).apply {
        CFStringGetCString(this@toKString, this, size, kCFStringEncodingUTF8)
    }.toKString()
}

internal fun CFNumberRef.toKULong(): ULong = memScoped {
    alloc<ULongVar> {
        CFNumberGetValue(this@toKULong, kCFNumberSInt64Type, ptr)
    }.value
}
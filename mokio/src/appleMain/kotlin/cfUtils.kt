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
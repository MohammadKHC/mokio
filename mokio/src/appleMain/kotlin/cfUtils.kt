package com.mohammedkhc.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreFoundation.CFNumberGetType
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFNumberLongType
import platform.CoreFoundation.kCFNumberSInt64Type
import platform.CoreFoundation.kCFStringEncodingUTF8

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
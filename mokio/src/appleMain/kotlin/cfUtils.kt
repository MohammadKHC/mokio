package com.mohammedkhc.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFNumberLongType
import platform.CoreFoundation.kCFStringEncodingUTF8

internal fun getKString(stringRef: CFStringRef): String = memScoped {
    val size = CFStringGetMaximumSizeForEncoding(
        CFStringGetLength(stringRef),
        kCFStringEncodingUTF8
    ) + 1

    allocArray<ByteVar>(size) {
        CFStringGetCString(stringRef, ptr, size, kCFStringEncodingUTF8)
    }.toKString()
}

internal fun getKLong(numberRef: CFNumberRef): Long = memScoped {
    alloc<LongVar> {
        CFNumberGetValue(numberRef, kCFNumberLongType, ptr)
    }.value
}
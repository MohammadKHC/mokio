package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFArrayRefVar
import platform.CoreFoundation.CFDictionaryGetCount
import platform.CoreFoundation.CFDictionaryGetTypeID
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFNumberType
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringGetTypeID
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFStringRefVar
import platform.CoreFoundation.kCFNumberLongType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreServices.*
import platform.darwin.dispatch_queue_create

actual class FileWatcher actual constructor(
    private val path: Path,
    recursive: Boolean,
    private val events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private var ref: StableRef<FileWatcher>? = null
    private var streamRef: FSEventStreamRef? = null

    actual fun startWatching() {
        ref = StableRef.create(this)
        val context = cValue<FSEventStreamContext> {
            info = ref!!.asCPointer()
            version = 0
        }
        val pathsToWatch = CFArrayCreate(null, createValues(1) {
            value = path.toString().toCFStringRef()
        }, 1, null)
        streamRef = FSEventStreamCreate(
            allocator = null,
            callback = staticCFunction { _: FSEventStreamRef?,
                                         clientCallBackInfo: COpaquePointer?,
                                         numEvents: ULong,
                                         eventPaths: COpaquePointer?,
                                         eventFlags: CPointer<UIntVar>?,
                                         _: CPointer<ULongVar>? ->
                clientCallBackInfo!!
                    .asStableRef<FileWatcher>().get()
                    .dispatchEvents(numEvents.toLong(), eventPaths?.reinterpret() ?: return@staticCFunction, eventFlags ?: return@staticCFunction)
            },
            context = context,
            pathsToWatch = pathsToWatch,
            sinceWhen = kFSEventStreamEventIdSinceNow,
            latency = 0.2,
            flags = kFSEventStreamCreateFlagFileEvents or
                    kFSEventStreamCreateFlagNoDefer or
                    kFSEventStreamCreateFlagUseCFTypes or
                    kFSEventStreamCreateFlagUseExtendedData
        )
        FSEventStreamSetDispatchQueue(
            streamRef,
            dispatch_queue_create("FileWatcher", null)
        )
        FSEventStreamStart(streamRef)
    }

    fun dispatchEvents(
        eventsCount: Long,
        eventPaths: CFArrayRef,
        eventFlags: CPointer<UIntVar>
    ) {
        println("event count: $eventsCount")
        for (i in 0L until eventsCount) {
            val data = CFArrayGetValueAtIndex(eventPaths, i) ?: continue
            val (path, fileId) = when (CFGetTypeID(data)) {
                CFDictionaryGetTypeID() -> {
                    println("is dict.")
                    val cfPath: CFStringRef? = null //CFDictionaryGetValue(
                //        data.reinterpret(),
                 //       kFSEventStreamEventExtendedDataPathKey.toCFStringRef()
                 //   )?.reinterpret()
                    if (cfPath == null) {
                        println("cfPath is null.")
                        val flags = eventFlags[i]
                        if (flags and kFSEventStreamEventFlagItemCreated != 0u)
                            print(" created")
                        if (flags and kFSEventStreamEventFlagItemModified != 0u)
                            print(" modified")
                        if (flags and kFSEventStreamEventFlagItemXattrMod != 0u)
                            print(" xattr")
                        if (flags and kFSEventStreamEventFlagItemRemoved != 0u)
                            print(" removed")
                        if (flags and kFSEventStreamEventFlagItemRenamed != 0u)
                            print(" renamed")
                        if (flags and kFSEventStreamEventFlagItemCloned != 0u)
                            print(" cloned")
                        if (flags and kFSEventStreamEventFlagItemInodeMetaMod != 0u)
                            print(" inodeMetaMod")
                        if (flags and kFSEventStreamEventFlagItemChangeOwner != 0u)
                            print(" owner changed.")
                        println()
                        continue
                    }
                    cfPath.toKString().toPath() to 0
                }

                CFStringGetTypeID() -> {
                    println("is cf string.")
                    val cfPath: CFStringRef = data.reinterpret()
                    cfPath.toKString().toPath() to 0
                }

                else -> {
                    println("is unknown.")
                    continue
                }
            }

            print(path)
            val flags = eventFlags[i]
            if (flags and kFSEventStreamEventFlagItemCreated != 0u)
                print(" created")
            if (flags and kFSEventStreamEventFlagItemModified != 0u)
                print(" modified")
            if (flags and kFSEventStreamEventFlagItemXattrMod != 0u)
                print(" xattr")
            if (flags and kFSEventStreamEventFlagItemRemoved != 0u)
                print(" removed")
            if (flags and kFSEventStreamEventFlagItemRenamed != 0u)
                print(" renamed")
            if (flags and kFSEventStreamEventFlagItemCloned != 0u)
                print(" cloned")
            if (flags and kFSEventStreamEventFlagItemInodeMetaMod != 0u)
                print(" inodeMetaMod")
            if (flags and kFSEventStreamEventFlagItemChangeOwner != 0u)
                print(" owner changed.")
            println()

            if (flags and kFSEventStreamEventFlagItemCreated != 0u && FileChangeEvent.Create in events) {
                onEvent(FileChangeEvent.Create, path)
            }

            if (flags and kFSEventStreamEventFlagItemModified != 0u && FileChangeEvent.Modify in events) {
                onEvent(FileChangeEvent.Modify, path)
            }

            if (flags and (kFSEventStreamEventFlagItemInodeMetaMod or
                        kFSEventStreamEventFlagItemChangeOwner or
                        kFSEventStreamEventFlagItemXattrMod) != 0u && FileChangeEvent.Delete in events
            ) {
                onEvent(FileChangeEvent.Attributes, path)
            }

            if (flags and kFSEventStreamEventFlagItemRemoved != 0u && FileChangeEvent.Delete in events) {
                onEvent(FileChangeEvent.Delete, path)
            }
        }
    }

    actual fun stopWatching() {
        FSEventStreamStop(streamRef)
        FSEventStreamInvalidate(streamRef)
        FSEventStreamRelease(streamRef)
        streamRef = null
        ref?.dispose()
        ref = null
    }
}
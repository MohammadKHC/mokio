package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.FileSystem
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
import platform.CoreFoundation.CFNumberGetTypeID
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
    private val recursive: Boolean,
    private val events: Set<FileChangeEvent>,
    private val onEvent: FileEventListener
) {
    private var ref: StableRef<FileWatcher>? = null
    private var streamRef: FSEventStreamRef? = null
    private val canonicalizedPath = FileSystem.SYSTEM.canonicalize(path)

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
                    .dispatchEvents(
                        numEvents.toLong(),
                        eventPaths?.reinterpret() ?: return@staticCFunction,
                        eventFlags ?: return@staticCFunction
                    )
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

    private val pendingRenames = mutableMapOf<ULong, Path>()

    fun dispatchEvents(
        eventsCount: Long,
        eventPaths: CFArrayRef,
        eventFlags: CPointer<UIntVar>
    ) {
        println("event count: $eventsCount")
        for (i in 0L until eventsCount) {
            val pathDict: CFDictionaryRef = CFArrayGetValueAtIndex(
                eventPaths,
                i
            )!!.reinterpret()
            val pathRef: CFStringRef = CFDictionaryGetValue(
                pathDict,
                kFSEventStreamEventExtendedDataPathKey.toCFStringRef()
            )!!.reinterpret()
            val path = pathRef.toKString().toPath()
            if (!recursive &&
                path != this.path && path.parent != this.path &&
                path != canonicalizedPath && path.parent != canonicalizedPath
            ) continue

            print("$path")
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

            if (flags and kFSEventStreamEventFlagItemRenamed != 0u) {
                val fileIdRef: CFNumberRef = CFDictionaryGetValue(
                    pathDict,
                    kFSEventStreamEventExtendedFileIDKey.toCFStringRef()
                )!!.reinterpret()
                val fileId = fileIdRef.toKULong()
                val oldPath = pendingRenames.remove(fileId)
                if (oldPath == null) {
                    pendingRenames[fileId] = path
                } else {
                    if (FileChangeEvent.Delete in events) {
                        onEvent(FileChangeEvent.Delete, oldPath)
                    }
                    if (FileChangeEvent.Create in events) {
                        onEvent(FileChangeEvent.Create, path)
                    }
                }
            }

            if (flags and kFSEventStreamEventFlagItemCreated != 0u && FileChangeEvent.Create in events
                // For some reason when a file is atomically moved we receive create alongside rename!
                && (flags and kFSEventStreamEventFlagItemRenamed == 0u || FileSystem.SYSTEM.exists(path))) {
                onEvent(FileChangeEvent.Create, path)
            }
            if (flags and kFSEventStreamEventFlagItemModified != 0u && FileChangeEvent.Modify in events) {
                onEvent(FileChangeEvent.Modify, path)
            }
            if (flags and FS_EVENTS_ATTRIBUTES_MASK != 0u && FileChangeEvent.Attributes in events) {
                onEvent(FileChangeEvent.Attributes, path)
            }
            if (flags and kFSEventStreamEventFlagItemRemoved != 0u && FileChangeEvent.Delete in events) {
                onEvent(FileChangeEvent.Delete, path)
            }
        }

        if (pendingRenames.isNotEmpty()) {
            pendingRenames.values.removeAll {
                if (FileChangeEvent.Delete in events) {
                    onEvent(FileChangeEvent.Delete, it)
                }
                true
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

    private companion object {
        val FS_EVENTS_ATTRIBUTES_MASK = kFSEventStreamEventFlagItemInodeMetaMod or
                kFSEventStreamEventFlagItemChangeOwner or
                kFSEventStreamEventFlagItemXattrMod
    }
}
package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFStringCreateWithCString
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
            value = CFStringCreateWithCString(null, path.toString(), kCFStringEncodingUTF8)
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
                    .dispatchEvents(numEvents.toLong(), eventPaths!!.reinterpret(), eventFlags!!)
            },
            context = context,
            pathsToWatch = pathsToWatch,
            sinceWhen = kFSEventStreamEventIdSinceNow,
            latency = 0.2,
            flags = kFSEventStreamCreateFlagFileEvents or kFSEventStreamCreateFlagNoDefer
        )
        FSEventStreamSetDispatchQueue(
            streamRef,
            dispatch_queue_create("FileWatcher", null)
        )
        FSEventStreamStart(streamRef)
    }

    fun dispatchEvents(
        eventsCount: Long,
        eventPaths: CPointer<CPointerVar<ByteVar>>,
        eventFlags: CPointer<UIntVar>
    ) {
        for (i in 0L until eventsCount) {
            val flags = eventFlags[i]
            print(eventPaths[i]!!.toKString())
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
                onEvent(FileChangeEvent.Create, eventPaths[i]!!.toKString().toPath())
            }

            if (flags and kFSEventStreamEventFlagItemModified != 0u && FileChangeEvent.Modify in events) {
                onEvent(FileChangeEvent.Modify, eventPaths[i]!!.toKString().toPath())
            }

            if (flags and (kFSEventStreamEventFlagItemInodeMetaMod or
                        kFSEventStreamEventFlagItemChangeOwner or
                        kFSEventStreamEventFlagItemXattrMod) != 0u && FileChangeEvent.Delete in events
            ) {
                onEvent(FileChangeEvent.Attributes, eventPaths[i]!!.toKString().toPath())
            }

            if (flags and kFSEventStreamEventFlagItemRemoved != 0u && FileChangeEvent.Delete in events) {
                onEvent(FileChangeEvent.Delete, eventPaths[i]!!.toKString().toPath())
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
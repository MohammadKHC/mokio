package com.mohammedkhc.io

import kotlinx.cinterop.*
import okio.Path
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreServices.*
import platform.darwin.dispatch_queue_create

actual class FileWatcher actual constructor(
    private val path: Path,
    recursive: Boolean,
    events: Set<FileChangeEvent>,
    onEvent: FileEventListener
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
            callback = staticCFunction(::dispatchEvents),
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
        streamRef: FSEventStreamRef?,
        clientCallBackInfo: COpaquePointer?,
        numEvents: ULong,
        eventPaths: COpaquePointer?,
        eventFlags: CPointer<UIntVar>?,
        eventIds: CPointer<ULongVar>?
    ) {
        error("$streamRef, $clientCallBackInfo, $numEvents, $eventPaths, $eventFlags, $eventIds")
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
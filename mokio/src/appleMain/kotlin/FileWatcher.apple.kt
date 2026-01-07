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
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreFoundation.*
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
            latency = 0.0,
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

    private val createdPaths = mutableSetOf<Path>()
    private val pendingRenames = mutableMapOf<ULong, Path>()

    fun dispatchEvents(
        eventsCount: Long,
        eventPaths: CFArrayRef,
        eventFlags: CPointer<UIntVar>
    ) {
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

            val flags = eventFlags[i]
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
                        createdPaths += path
                        onEvent(FileChangeEvent.Create, path)
                    }
                }
            }

            if (flags and kFSEventStreamEventFlagItemCreated != 0u && FileChangeEvent.Create in events
                // macOS seems to send the create event multiple times.
                // So we keep track of the created paths.
                && path !in createdPaths
            ) {
                createdPaths += path
                onEvent(FileChangeEvent.Create, path)
            }
            if (flags and kFSEventStreamEventFlagItemModified != 0u && FileChangeEvent.Modify in events) {
                onEvent(FileChangeEvent.Modify, path)
            }
            if (flags and FS_EVENTS_ATTRIBUTES_MASK != 0u && FileChangeEvent.Attributes in events) {
                onEvent(FileChangeEvent.Attributes, path)
            }
            if (flags and kFSEventStreamEventFlagItemRemoved != 0u && FileChangeEvent.Delete in events) {
                createdPaths -= path
                onEvent(FileChangeEvent.Delete, path)
            }
        }

        if (pendingRenames.isNotEmpty()) {
            pendingRenames.values.removeAll {
                if (FileChangeEvent.Delete in events) {
                    createdPaths -= path
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
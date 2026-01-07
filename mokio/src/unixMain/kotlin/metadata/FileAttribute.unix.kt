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

package com.mohammedkhc.io.metadata

import com.mohammedkhc.io.checkNotNegativeOne
import com.mohammedkhc.io.ensureSuccess
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.cinterop.createValues
import okio.Path
import platform.posix.*
import kotlin.time.Instant

internal actual fun systemSetFileAttributes(
    path: Path,
    attributes: Array<out FileAttribute>,
    followLinks: Boolean
) {
    var setTimes = false

    for (attribute in attributes) {
        when (attribute) {
            is FileTimeAttribute -> setTimes = true

            is FileModeAttribute -> if (followLinks) {
                @OptIn(UnsafeNumber::class)
                chmod(path.toString(), attribute.mode.rawMode.convert())
            } else {
                @OptIn(UnsafeNumber::class)
                lchmod(path.toString(), attribute.mode.rawMode.convert())
            }.ensureSuccess()

            is FileOwnerAttribute -> if (followLinks) {
                chown(path.toString(), attribute.userId, attribute.groupId)
            } else {
                lchown(path.toString(), attribute.userId, attribute.groupId)
            }.ensureSuccess()

            is WindowsFileAttribute -> throw UnsupportedOperationException()
        }
    }

    if (setTimes) {
        val fd = open(
            path.toString(),
            O_RDONLY or (if (followLinks) 0 else O_NOFOLLOW)
        ).checkNotNegativeOne()
        try {
            val lastAccessTime = attributes.get<LastAccessTime>()?.time
            val lastModifiedTime = attributes.get<LastModifiedTime>()?.time
            if (lastModifiedTime != null || lastAccessTime != null) {
                futimens(
                    fd,
                    createValues(2) { i ->
                        when (i) {
                            0 -> lastAccessTime?.let {
                                tv_sec = it.epochSeconds
                                tv_nsec = it.nanosecondsOfSecond.toLong()
                            } ?: run { tv_nsec = UTIME_OMIT }

                            1 -> lastModifiedTime?.let {
                                tv_sec = it.epochSeconds
                                tv_nsec = it.nanosecondsOfSecond.toLong()
                            } ?: run { tv_nsec = UTIME_OMIT }
                        }
                    }
                ).ensureSuccess()
            }
            attributes.get<CreationTime>()?.time?.let {
                systemSetFileCreationTime(fd, it, followLinks)
            }
        } finally {
            close(fd).ensureSuccess()
        }
    }
}

internal expect fun systemSetFileCreationTime(
    fd: Int,
    creationTime: Instant,
    followLinks: Boolean
)

private const val UTIME_OMIT: Long = (1 shl 30) - 2
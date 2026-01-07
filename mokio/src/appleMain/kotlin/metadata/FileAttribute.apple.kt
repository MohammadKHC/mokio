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

import com.mohammedkhc.io.ensureSuccess
import kotlinx.cinterop.cValue
import kotlinx.cinterop.sizeOf
import platform.osx.ATTR_BIT_MAP_COUNT
import platform.osx.ATTR_CMN_CRTIME
import platform.osx.FSOPT_NOFOLLOW
import platform.osx.attrlist
import platform.posix.fsetattrlist
import platform.posix.timespec
import kotlin.time.Instant

internal actual fun systemSetFileCreationTime(
    fd: Int,
    creationTime: Instant,
    followLinks: Boolean
) = fsetattrlist(
    fd,
    cValue<attrlist> {
        commonattr = ATTR_CMN_CRTIME.toUInt()
        bitmapcount = ATTR_BIT_MAP_COUNT.toUShort()
    },
    cValue<timespec> {
        tv_sec = creationTime.epochSeconds
        tv_nsec = creationTime.nanosecondsOfSecond.toLong()
    },
    sizeOf<timespec>().toULong(),
    if (followLinks) 0u else FSOPT_NOFOLLOW.toUInt()
).ensureSuccess()
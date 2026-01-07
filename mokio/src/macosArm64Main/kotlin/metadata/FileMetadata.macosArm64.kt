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

import com.mohammedkhc.io.FileMode
import com.mohammedkhc.io.ensureSuccess
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path
import platform.posix.lstat
import platform.posix.stat

internal actual fun systemFileMetadata(
    path: Path,
    followLinks: Boolean
): FileMetadata = memScoped {
    val stat = alloc<stat>()
    val result =
        if (followLinks) stat(path.toString(), stat.ptr)
        else lstat(path.toString(), stat.ptr)
    result.ensureSuccess()

    with(stat) {
        UnixFileMetadata(
            deviceId = st_dev.toLong(),
            inode = st_ino.toLong(),
            mode = FileMode(st_mode.toUInt()),
            linkCount = st_nlink.toInt(),
            userId = st_uid,
            groupId = st_gid,
            rawDeviceId = st_rdev.toLong(),
            changeTime = st_ctimespec.toInstant(),
            creationTime = st_birthtimespec.toInstant(),
            lastModifiedTime = st_mtimespec.toInstant(),
            lastAccessTime = st_atimespec.toInstant(),
            size = st_size
        )
    }
}
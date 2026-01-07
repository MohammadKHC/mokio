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
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import kotlin.time.Instant

sealed interface FileMetadata {
    val isRegularFile: Boolean
    val isDirectory: Boolean
    val isSymbolicLink: Boolean
    val isOther: Boolean
    val creationTime: Instant
    val lastModifiedTime: Instant
    val lastAccessTime: Instant
    val size: Long
}

data class BasicFileMetadata(
    override val isRegularFile: Boolean,
    override val isDirectory: Boolean,
    override val isSymbolicLink: Boolean,
    override val isOther: Boolean,
    override val creationTime: Instant,
    override val lastModifiedTime: Instant,
    override val lastAccessTime: Instant,
    override val size: Long,
) : FileMetadata

data class UnixFileMetadata(
    val deviceId: Long,
    val inode: Long,
    val mode: FileMode,
    val linkCount: Int,
    val userId: UInt,
    val groupId: UInt,
    val rawDeviceId: Long,
    val changeTime: Instant,
    override val creationTime: Instant,
    override val lastModifiedTime: Instant,
    override val lastAccessTime: Instant,
    override val size: Long,
) : FileMetadata {
    override val isRegularFile get() = mode.isRegularFile
    override val isDirectory get() = mode.isDirectory
    override val isSymbolicLink get() = mode.isSymbolicLink
    override val isOther get() = mode.isOther
}

data class WindowsFileMetadata(
    val isReadOnly: Boolean,
    val isArchive: Boolean,
    val isSystem: Boolean,
    val isHidden: Boolean,
    override val isRegularFile: Boolean,
    override val isDirectory: Boolean,
    override val isSymbolicLink: Boolean,
    override val isOther: Boolean,
    override val creationTime: Instant,
    override val lastModifiedTime: Instant,
    override val lastAccessTime: Instant,
    override val size: Long,
) : FileMetadata


fun FileSystem.extendedMetadata(path: Path, followLinks: Boolean = true): FileMetadata = when (this) {
    FileSystem.SYSTEM -> systemFileMetadata(path, followLinks)
    else -> basicMetadata(path, followLinks)
}

internal expect fun systemFileMetadata(path: Path, followLinks: Boolean): FileMetadata

private fun FileSystem.basicMetadata(path: Path, followLinks: Boolean): FileMetadata {
    val metadata = if (followLinks) run {
        var currentPath = path
        val visited = mutableSetOf<Path>()
        repeat(40) {
            val metadata = metadata(currentPath)
            val symlinkTarget = metadata.symlinkTarget ?: return@run metadata
            currentPath = currentPath.parent?.resolve(symlinkTarget) ?: symlinkTarget
            if (!visited.add(currentPath)) {
                throw IOException("Too many levels of symbolic links.")
            }
        }
        throw IOException("Too many levels of symbolic links.")
    } else metadata(path)

    return with(metadata) {
        BasicFileMetadata(
            isRegularFile = isRegularFile,
            isDirectory = isDirectory,
            isSymbolicLink = symlinkTarget != null,
            isOther = !isRegularFile && !isDirectory && symlinkTarget == null,
            creationTime = Instant.fromEpochMilliseconds(createdAtMillis ?: 0),
            lastModifiedTime = Instant.fromEpochMilliseconds(lastModifiedAtMillis ?: 0),
            lastAccessTime = Instant.fromEpochMilliseconds(lastAccessedAtMillis ?: 0),
            size = size ?: 0
        )
    }
}
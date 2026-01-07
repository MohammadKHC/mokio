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
import okio.Path
import okio.SYSTEM
import kotlin.jvm.JvmInline
import kotlin.time.Instant

sealed interface FileAttribute

sealed interface FileTimeAttribute : FileAttribute {
    val time: Instant
}

@JvmInline
value class CreationTime(override val time: Instant) : FileTimeAttribute

@JvmInline
value class LastModifiedTime(override val time: Instant) : FileTimeAttribute

@JvmInline
value class LastAccessTime(override val time: Instant) : FileTimeAttribute

sealed interface UnixFileAttribute : FileAttribute

@JvmInline
value class FileModeAttribute(val mode: FileMode) : UnixFileAttribute
data class FileOwnerAttribute(val userId: UInt, val groupId: UInt) : UnixFileAttribute

sealed interface WindowsFileAttribute : FileAttribute {
    val enabled: Boolean

    @JvmInline
    value class ReadOnly(override val enabled: Boolean) : WindowsFileAttribute

    @JvmInline
    value class Archive(override val enabled: Boolean) : WindowsFileAttribute

    @JvmInline
    value class System(override val enabled: Boolean) : WindowsFileAttribute

    @JvmInline
    value class Hidden(override val enabled: Boolean) : WindowsFileAttribute
}

fun FileSystem.setAttributes(
    path: Path,
    vararg attributes: FileAttribute,
    followLinks: Boolean = true
) {
    require(this == FileSystem.SYSTEM) { "Not supported" }
    require(attributes.isNotEmpty()) { "attributes can't be empty." }
    systemSetFileAttributes(path, attributes, followLinks)
}

internal expect fun systemSetFileAttributes(
    path: Path,
    attributes: Array<out FileAttribute>,
    followLinks: Boolean
)

internal inline fun <reified T : FileAttribute> Array<out FileAttribute>.get(): T? =
    firstOrNull { it is T } as? T
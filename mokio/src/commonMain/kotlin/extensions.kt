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

import com.mohammedkhc.io.metadata.LastModifiedTime
import com.mohammedkhc.io.metadata.extendedMetadata
import com.mohammedkhc.io.metadata.setAttributes
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

fun FileSystem.isRegularFile(path: Path) =
    extendedMetadata(path).isRegularFile

fun FileSystem.isDirectory(path: Path) =
    extendedMetadata(path).isDirectory

fun FileSystem.setLastModifiedTime(path: Path, time: Instant, followLinks: Boolean = true) =
    setAttributes(path, LastModifiedTime(time), followLinks = followLinks)

fun Path.startsWith(other: Path): Boolean {
    if (root != other.root) return false
    if (segments.size < other.segments.size) return false
    return segments.subList(0, other.segments.size) == other.segments
}
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

import okio.*
import okio.FileMetadata
import okio.Path.Companion.toPath

/**
 * Creates a symbolic link at [source] that resolves to [target].
 *
 * Differences from [FileSystem.createSymlink]
 * 1. this does work on Android 5.0+, unlike [FileSystem.createSymlink] which requires Android 8.0+.
 * 2. this does work on mingwX64.
 *
 * @throws IOException If any error happens.
 */
fun FileSystem.createSymbolicLink(source: Path, target: Path) = when (this) {
    FileSystem.SYSTEM -> systemCreateSymbolicLink(source, target.toString())
    else -> createSymlink(source, target)
}

/**
 * Returns the target of the given [symlink].
 *
 * Differences from [FileMetadata.symlinkTarget]
 * 1. this does work on Android 5.0+, unlike [FileMetadata.symlinkTarget] which requires Android 8.0+.
 * 2. this does work on mingwX64.
 *
 * @throws IOException If any error happens.
 */
fun FileSystem.readSymbolicLink(symlink: Path): Path = when (this) {
    FileSystem.SYSTEM -> systemReadSymbolicLink(symlink).toPath()
    else -> metadata(symlink).symlinkTarget
        ?: throw IOException("Not a symbolic link.")
}

/**
 * Creates a symbolic link at [source] that resolves **exactly** to [target].
 *
 * Differences from [FileSystem.createSymlink]
 * 1. The target is a [String] and not a [Path] because a [Path] cannot start with a leading dot.
 * 2. this does work on Android 5.0+, unlike [FileSystem.createSymlink] which requires Android 8.0+.
 * 3. this does work on mingwX64.
 *
 * Note: When [this] is not [FileSystem.Companion.SYSTEM]
 * this function fallbacks to [FileSystem.createSymlink]
 *
 * @throws IOException If any error happens.
 */
fun FileSystem.createSymbolicLinkExact(source: Path, target: String) = when (this) {
    FileSystem.SYSTEM -> systemCreateSymbolicLink(source, target)
    else -> createSymlink(source, target.toPath())
}

/**
 * Returns the target of the given [symlink].
 *
 * Differences from [FileMetadata.symlinkTarget]
 * 1. The return value is a [String] and not a [Path] because a [Path] cannot start with a leading dot.
 * 2. this does work on Android 5.0+, unlike [FileMetadata.symlinkTarget] which requires Android 8.0+.
 * 3. this does work on mingwX64.
 *
 * Note: When [this] is not [FileSystem.Companion.SYSTEM]
 * this function fallbacks to [FileMetadata.symlinkTarget]
 *
 * @throws IOException If any error happens.
 */
fun FileSystem.readSymbolicLinkExact(symlink: Path): String = when (this) {
    FileSystem.SYSTEM -> systemReadSymbolicLink(symlink)
    else -> metadata(symlink).symlinkTarget?.toString()
        ?: throw IOException("Not a symbolic link.")
}

internal expect fun systemCreateSymbolicLink(source: Path, target: String)
internal expect fun systemReadSymbolicLink(symlink: Path): String
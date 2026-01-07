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

import kotlinx.cinterop.get
import kotlinx.cinterop.sizeOf
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.windows.*

internal actual fun systemCreateSymbolicLink(source: Path, target: String) {
    val targetPath = source.parent?.resolve(target) ?: target.toPath()
    // CreateSymbolicLinkW requires knowing whether the target is a directory.
    val isDirectory = try {
        FileSystem.SYSTEM.isDirectory(targetPath)
    } catch (_: IOException) {
        // Assume that it's not a directory.
        false
    }
    val flags = when {
        isDirectory -> SYMBOLIC_LINK_FLAG_DIRECTORY.toUInt()
        else -> 0u
    }

    val result = CreateSymbolicLinkW(
        source.toString(),
        target,
        flags
    ).toInt()
    if (result == 0) {
        // Try again with the SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE flag.
        CreateSymbolicLinkW(
            source.toString(),
            target,
            flags or SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE.toUInt()
        ).toInt().ensureSuccess()
    }
}

internal actual fun systemReadSymbolicLink(symlink: Path): String {
    return useHandle(symlink, false) {
        val data = getReparseData()
        if (data.ReparseTag != IO_REPARSE_TAG_SYMLINK) {
            throw IOException("Not a symbolic link.")
        }

        data.SymbolicLinkReparseBuffer.run {
            val charSize = sizeOf<WCHARVar>().toInt()
            val length = SubstituteNameLength.toInt() / charSize
            val offset = SubstituteNameOffset.toInt() / charSize
            CharArray(length) {
                PathBuffer[offset + it].toInt().toChar()
            }.concatToString().removePrefix("\\??\\")
        }
    }
}
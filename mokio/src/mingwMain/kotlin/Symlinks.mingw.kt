package com.mohammedkhc.io

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.windows.CreateSymbolicLinkW
import platform.windows.IO_REPARSE_TAG_SYMLINK
import platform.windows.SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE
import platform.windows.SYMBOLIC_LINK_FLAG_DIRECTORY

internal actual fun systemCreateSymbolicLink(source: Path, target: String) {
    val targetPath = source.parent?.resolve(target) ?: target.toPath()
    // CreateSymbolicLinkW requires knowing whether the target is a directory.
    val isDirectory = try {
        FileSystem.SYSTEM.stat(targetPath).mode.isDirectory
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
    val data = getReparseData(symlink)
    require(data.ReparseTag == IO_REPARSE_TAG_SYMLINK) {
        "Not a symbolic link."
    }

    return data.symlinkTarget
}
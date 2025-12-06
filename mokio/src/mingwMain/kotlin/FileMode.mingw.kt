package com.mohammedkhc.io

import okio.Path
import platform.windows.FILE_ATTRIBUTE_READONLY
import platform.windows.GetFileAttributesW
import platform.windows.SetFileAttributesW

internal actual fun systemSetFileMode(path: Path, mode: FileMode) {
    val file = path.toString()
    val isReadOnly = FileMode.Permission.OwnerWrite !in mode.permissions
    val attributes = GetFileAttributesW(file).apply {
        if (isReadOnly) this or FILE_ATTRIBUTE_READONLY.toUInt()
        else this and FILE_ATTRIBUTE_READONLY.toUInt().inv()
    }
    SetFileAttributesW(file, attributes)
        .ensureSuccess()
}
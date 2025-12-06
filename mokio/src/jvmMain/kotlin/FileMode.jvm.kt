package com.mohammedkhc.io

import okio.Path
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

actual fun systemSetFileMode(path: Path, mode: FileMode) {
    val permissions = mode.permissions
    if ("posix" in FileSystems.getDefault().supportedFileAttributeViews()) {
        Files.setPosixFilePermissions(
            path.toNioPath(),
            permissions.mapTo(mutableSetOf()) {
                PosixFilePermission.entries[it.ordinal]
            }
        )
    }

    // Fallback implementation for non-posix systems.
    val file = path.toFile()
    file.setReadable(FileMode.Permission.OwnerRead in permissions)
    file.setWritable(FileMode.Permission.OwnerWrite in permissions)
    file.setExecutable(FileMode.Permission.OwnerExecute in permissions)
}
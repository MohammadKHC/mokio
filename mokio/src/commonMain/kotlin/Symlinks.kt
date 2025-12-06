package com.mohammedkhc.io

import okio.*
import okio.Path.Companion.toPath

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
 */
@Throws(IOException::class)
fun FileSystem.createSymbolicLink(source: Path, target: String) = when (this) {
    FileSystem.SYSTEM -> systemCreateSymbolicLink(source, target)
    else -> createSymlink(source, target.toPath())
}

/**
 * Returns the target of the given [symlink].
 *
 * Differences from [FileSystem.metadata] and [FileMetadata.symlinkTarget]
 * 1. The return value is a [String] and not a [Path] because a [Path] cannot start with a leading dot.
 * 2. this does work on Android 5.0 onward, unlike [FileMetadata.symlinkTarget] which requires Android 8.0.
 * 3. this does work on all Android versions.
 *
 * Note: When [this] is not [FileSystem.Companion.SYSTEM]
 * this function fallbacks to [FileSystem.metadata] and [FileMetadata.symlinkTarget]
 */
@Throws(IOException::class)
fun FileSystem.readSymbolicLink(symlink: Path): String = when (this) {
    FileSystem.SYSTEM -> systemReadSymbolicLink(symlink)
    else -> metadata(symlink).symlinkTarget?.toString()
        ?: throw IOException("Not a symbolic link.")
}

internal expect fun systemCreateSymbolicLink(source: Path, target: String)
internal expect fun systemReadSymbolicLink(symlink: Path): String

internal fun <T> getRealPath(
    path: Path,
    metadata: (Path) -> T,
    symlinkTarget: (T) -> Path?
): T {
    var currentPath = path
    val visited = mutableSetOf<Path>()
    repeat(40) {
        val metadata = metadata(currentPath)
        val symlinkTarget = symlinkTarget(metadata) ?: return metadata
        currentPath = currentPath.parent?.resolve(symlinkTarget) ?: symlinkTarget
        if (!visited.add(currentPath)) {
            throw IOException("Too many levels of symbolic links.")
        }
    }
    throw IOException("Too many levels of symbolic links.")
}
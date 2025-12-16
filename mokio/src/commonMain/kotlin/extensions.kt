package com.mohammedkhc.io

import okio.FileSystem
import okio.Path

fun FileSystem.isRegularFile(path: Path) =
    metadataOrNull(path)?.isRegularFile == true

fun FileSystem.isDirectory(path: Path) =
    metadataOrNull(path)?.isDirectory == true

fun Path.startsWith(other: Path): Boolean {
    if (root != other.root) return false
    if (segments.size < other.segments.size) return false
    return segments.subList(0, other.segments.size) == other.segments
}
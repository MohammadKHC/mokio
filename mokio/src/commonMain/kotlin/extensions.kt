package com.mohammedkhc.io

import okio.FileSystem
import okio.Path

fun FileSystem.isRegularFile(path: Path) =
    metadataOrNull(path)?.isRegularFile == true

fun FileSystem.isDirectory(path: Path) =
    metadataOrNull(path)?.isDirectory == true
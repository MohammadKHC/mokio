package com.mohammedkhc.io

import okio.Path
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlin.time.toKotlinInstant

internal actual fun systemStat(path: Path, followSymlinks: Boolean): Stat {
    val nioPath = path.toNioPath()

    val options =
        if (followSymlinks) emptyArray()
        else arrayOf(LinkOption.NOFOLLOW_LINKS)

    if ("unix" in FileSystems.getDefault().supportedFileAttributeViews()) {
        val attrs = Files.readAttributes(nioPath, "unix:*", *options)
        return Stat(
            deviceId = (attrs["dev"] as Long).toUInt(),
            inode = (attrs["ino"] as Long).toUInt(),
            mode = FileMode((attrs["mode"] as Int).toUInt()),
            userId = (attrs["uid"] as Int).toUInt(),
            groupId = (attrs["gid"] as Int).toUInt(),
            size = attrs["size"] as Long,
            changeTime = (attrs["ctime"] as FileTime).toInstant().toKotlinInstant(),
            modificationTime = (attrs["lastModifiedTime"] as FileTime).toInstant().toKotlinInstant()
        )
    }

    // Fallback implementation for non-unix systems.
    return Files.readAttributes(nioPath, BasicFileAttributes::class.java, *options).run {
        Stat(
            deviceId = 0u,
            inode = 0u,
            mode = FileMode(
                when {
                    isRegularFile -> FileMode.Type.RegularFile
                    isDirectory -> FileMode.Type.Directory
                    isSymbolicLink -> FileMode.Type.SymbolicLink
                    else -> FileMode.Type.RegularFile
                }, FileMode.Permission.DEFAULT
            ),
            userId = 0u,
            groupId = 0u,
            size = if (isSymbolicLink) {
                Files.readSymbolicLink(nioPath).toString().length.toLong()
            } else size(),
            changeTime = creationTime().toInstant().toKotlinInstant(),
            modificationTime = lastModifiedTime().toInstant().toKotlinInstant()
        )
    }
}
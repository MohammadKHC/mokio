package com.mohammedkhc.io

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import kotlin.jvm.JvmInline

@JvmInline
value class FileMode(val rawMode: UInt) {
    constructor(type: Type?, permissions: Set<Permission>) : this(
        (type?.value ?: 0u) or permissions.fold(0u) { acc, it ->
            acc or it.bit
        }
    )

    val type
        get() = Type.entries.firstOrNull(::matchesType)

    val permissions
        get() = Permission.entries.filterTo(
            mutableSetOf(),
            ::hasPermission
        )

    val rawString get() = rawMode.toString(8)

    inline val isRegularFile get() = matchesType(Type.RegularFile)
    inline val isDirectory get() = matchesType(Type.Directory)
    inline val isSymbolicLink get() = matchesType(Type.SymbolicLink)
    inline val isOther get() = !isRegularFile && !isDirectory && !isSymbolicLink

    override fun toString(): String {
        return "FileMode(type=$type, permissions=$permissions, raw: $rawString)"
    }

    @PublishedApi
    internal fun matchesType(type: Type) =
        type.value == rawMode and 0xF000u

    @PublishedApi
    internal fun hasPermission(permission: Permission) =
        permission.bit and rawMode != 0u

    enum class Type(internal val value: UInt) {
        Directory(0x4000u),
        CharacterDevice(0x2000u),
        BlockDevice(0x6000u),
        RegularFile(0x8000u),
        Fifo(0x1000u),
        SymbolicLink(0xA000u),
        Socket(0xC000u)
    }

    enum class Permission(internal val bit: UInt) {
        OwnerRead(0x100u),
        OwnerWrite(0x80u),
        OwnerExecute(0x40u),
        GroupRead(0x20u),
        GroupWrite(0x10u),
        GroupExecute(0x8u),
        OthersRead(0x4u),
        OthersWrite(0x2u),
        OthersExecute(0x1u);

        companion object {
            val DEFAULT = setOf(OwnerRead, OwnerWrite, GroupRead, OthersRead)
        }
    }
}

@Throws(IOException::class)
fun FileSystem.setFileMode(path: Path, mode: FileMode) {
    require(this == FileSystem.SYSTEM) { "Not supported" }
    systemSetFileMode(path, mode)
}

internal expect fun systemSetFileMode(path: Path, mode: FileMode)
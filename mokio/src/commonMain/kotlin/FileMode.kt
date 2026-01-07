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

import kotlin.jvm.JvmInline

@JvmInline
value class FileMode(val rawMode: UInt) {
    constructor(type: Type?, permissions: Set<Permission>) : this(
        (type?.rawValue ?: 0u) or permissions.fold(0u) { acc, it ->
            acc or it.rawValue
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
        type.rawValue == rawMode and 0xF000u

    @PublishedApi
    internal fun hasPermission(permission: Permission) =
        permission.rawValue and rawMode != 0u

    enum class Type(val rawValue: UInt) {
        Directory(0x4000u),
        CharacterDevice(0x2000u),
        BlockDevice(0x6000u),
        RegularFile(0x8000u),
        Fifo(0x1000u),
        SymbolicLink(0xA000u),
        Socket(0xC000u)
    }

    enum class Permission(val rawValue: UInt) {
        OwnerRead(0x100u),
        OwnerWrite(0x80u),
        OwnerExecute(0x40u),
        GroupRead(0x20u),
        GroupWrite(0x10u),
        GroupExecute(0x8u),
        OthersRead(0x4u),
        OthersWrite(0x2u),
        OthersExecute(0x1u)
    }
}
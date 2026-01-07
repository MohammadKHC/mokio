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

import android.system.ErrnoException
import android.system.Os
import okio.Path

internal actual fun systemCreateSymbolicLink(source: Path, target: String) = try {
    Os.symlink(target, source.toString())
} catch (e: ErrnoException) {
    throw e.toIOException()
}

internal actual fun systemReadSymbolicLink(symlink: Path): String = try {
    Os.readlink(symlink.toString())
} catch (e: ErrnoException) {
    throw e.toIOException()
}
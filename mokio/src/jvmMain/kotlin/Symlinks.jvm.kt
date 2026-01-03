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

import okio.Path
import java.nio.file.Files
import java.nio.file.Paths

internal actual fun systemCreateSymbolicLink(source: Path, target: String) {
    Files.createSymbolicLink(source.toNioPath(), Paths.get(target))
}

internal actual fun systemReadSymbolicLink(symlink: Path): String =
    Files.readSymbolicLink(symlink.toNioPath()).toString()
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

expect class FileWatcher(
    path: Path,
    recursive: Boolean = false,
    events: Set<FileChangeEvent> = FileChangeEvent.entries.toSet(),
    onEvent: FileEventListener
) {
    fun startWatching()
    fun stopWatching()
}

enum class FileChangeEvent {
    Create,
    Modify,
    Attributes,
    Delete
}

typealias FileEventListener = (event: FileChangeEvent, path: Path) -> Unit
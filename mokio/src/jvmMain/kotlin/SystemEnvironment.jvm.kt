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

actual object SystemEnvironment {
    actual operator fun get(name: String): String? =
        System.getenv(name)

    actual operator fun set(name: String, value: String) {
        throw UnsupportedOperationException()
    }

    actual operator fun minusAssign(name: String) {
        throw UnsupportedOperationException()
    }
}
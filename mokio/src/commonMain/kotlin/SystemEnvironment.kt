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

expect object SystemEnvironment {
    operator fun get(name: String): String?

    /**
     * Sets the environment variable with the given [name] to the given [value]
     *
     * @throws UnsupportedOperationException If the implementation doesn't support
     * changing the environment variables. (This is the case on the jvm)
     * @throws okio.IOException If any error happens.
     */
    operator fun set(name: String, value: String)

    /**
     * Unsets the environment variable with the given [name]
     *
     * @throws UnsupportedOperationException If the implementation doesn't support
     * changing the environment variables. (This is the case on the jvm)
     * @throws okio.IOException If any error happens.
     */
    operator fun minusAssign(name: String)
}
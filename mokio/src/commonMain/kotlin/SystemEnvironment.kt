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
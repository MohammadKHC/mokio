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
package com.mohammedkhc.io

import java.io.File

internal object JniLibraryLoader {
    private const val LIBRARY_NAME = "mokio_jni"

    fun load() {
        val arch = when (System.getProperty("os.arch")) {
            "amd64", "x86_64" -> "x64"
            "arm64", "aarch64" -> "arm64"
            else -> error("Unsupported arch.")
        }
        val libraryName = System.mapLibraryName("${LIBRARY_NAME}_$arch")
        val libraryFile = File.createTempFile(
            libraryName.substringBeforeLast('.'),
            libraryName.substring(libraryName.lastIndexOf('.'))
        )
        JniLibraryLoader.javaClass.classLoader.getResourceAsStream(libraryName)!!.use {
            libraryFile.outputStream().use(it::copyTo)
            libraryFile.deleteOnExit()
        }
        @Suppress("UnsafeDynamicallyLoadedCode")
        System.load(libraryFile.absolutePath)
    }
}
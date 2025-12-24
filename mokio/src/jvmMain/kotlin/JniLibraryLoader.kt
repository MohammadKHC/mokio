package com.mohammedkhc.io

import java.io.File

internal object JniLibraryLoader {
    private const val LIBRARY_NAME = "mokio_jni"

    fun load() {
        val libraryName = System.mapLibraryName(LIBRARY_NAME)
        val libraryFile = File.createTempFile(
            libraryName.substringBeforeLast('.'),
            libraryName.substringAfterLast('.')
        )
        JniLibraryLoader.javaClass.classLoader.getResourceAsStream(libraryName)!!.use {
            libraryFile.outputStream().use(it::copyTo)
            libraryFile.deleteOnExit()
        }
        @Suppress("UnsafeDynamicallyLoadedCode")
        System.load(libraryFile.absolutePath)
    }
}
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    val os = OperatingSystem.current()
    when {
        os.isLinux -> {
            linuxX64()
            linuxArm64()
        }

        os.isMacOsX -> {
            macosX64()
            macosArm64()
        }

        os.isWindows -> mingwX64()
        else -> throw GradleException("Host OS is not supported in Kotlin/Native")
    }


    targets.all {
        if (this !is KotlinNativeTarget) return@all
        compilations["main"].cinterops.create("jni") {
            packageName("kotlinx.jni")
            val jniDir = File(System.getProperty("java.home"), "include")
            val jniOsDirName = when {
                os.isLinux -> "linux"
                os.isMacOsX -> "darwin"
                os.isWindows -> "win32"
                else -> throw GradleException("Host OS is not supported in Kotlin/Native")
            }
            includeDirs(
                jniDir,
                jniDir.resolve(jniOsDirName)
            )
            header(jniDir.resolve("jni.h"))
        }

        binaries.sharedLib()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mokio"))
        }
    }

    compilerOptions {
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
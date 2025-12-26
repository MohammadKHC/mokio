import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    if (!HostManager.hostIsMingw) {
        linuxX64()
        linuxArm64()
    }
    if (HostManager.hostIsMac) {
        macosX64()
        macosArm64()
    }
    mingwX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations["main"].cinterops.create("jni") {
            packageName("kotlinx.jni")
            val jniDir = File(System.getProperty("java.home"), "include")
            includeDirs(
                jniDir,
                jniDir.resolve(HostManager.jniHostPlatformIncludeDir)
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
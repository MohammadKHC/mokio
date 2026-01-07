import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.konan.target.Family
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    id("maven-publish")
}

kotlin {
    jvm()
    androidLibrary {
        namespace = "com.mohammedkhc.io"
        compileSdk = 36
        minSdk = 23
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64 {
        compilations["main"].cinterops.create("reparse_data_buffer") {
            defFile(file("src/nativeInterop/mingw/reparse_data_buffer.def"))
        }
    }
    targets.withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family == Family.LINUX }
        .configureEach {
            compilations["main"].cinterops.create("statx") {
                defFile(file("src/nativeInterop/linux/statx.def"))
            }
        }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("native") {
                group("unix") {
                    group("linux")
                    group("apple")
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.okio.fakefilesystem)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
    }

    compilerOptions {
        allWarningsAsErrors = true
        extraWarnings = true
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

group = "com.mohammedkhc"
version = "1.0.0"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/MohammadKHC/mokio")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
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
        compilations["main"].cinterops.create("extra") {
            defFile(file("src/mingwX64Main/cinterop/extra.def"))
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
            implementation(libs.kotlin.coroutines)
            implementation(libs.okio)
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
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
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
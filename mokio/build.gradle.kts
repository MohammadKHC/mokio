import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName
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
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }
}

val supportedJniTargets = HostManager().enabled.filter {
    ((!HostManager.hostIsMingw && it.family == Family.LINUX)
            || it.family == Family.OSX || it.family == Family.MINGW) &&
            it !in KonanTarget.deprecatedTargets
}

supportedJniTargets.forEach {
    tasks.register<Copy>("packageMokioJniLibrary${it.presetName.capitalized}") {
        val jvmResourcesDir = sourceSets["jvmMain"].output.resourcesDir!!
        dependsOn(":mokio-jni:linkReleaseShared${it.presetName.capitalized}")
        val prefix = "${it.family.dynamicPrefix}mokio_jni"
        val suffix = ".${it.family.dynamicSuffix}"
        val path = "../mokio-jni/build/bin/${it.presetName}/releaseShared/$prefix$suffix"
        val newLibraryName = "${prefix}_${it.architecture.name.lowercase()}$suffix"
        inputs.file(path)
        from(path) { rename { newLibraryName } }
        outputs.file(jvmResourcesDir.resolve(newLibraryName))
        into(jvmResourcesDir)
    }
}
tasks["jvmProcessResources"].dependsOn(
    "packageMokioJniLibrary${HostManager.host.presetName.capitalized}"
)

val String.capitalized
    get() = replaceFirstChar(Char::uppercase)

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
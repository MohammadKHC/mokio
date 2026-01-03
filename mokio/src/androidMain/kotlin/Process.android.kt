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

import android.os.Build
import android.system.Os
import android.system.OsConstants
import okio.Path
import okio.sink
import okio.source

actual class Process actual constructor(
    command: List<String>,
    directory: Path?,
    environment: Map<String, String>?,
    redirectErrorSource: Boolean
) {
    private val process = ProcessBuilder(command).apply {
        directory?.toFile()?.let(::directory)
        if (environment != null) {
            environment().clear()
            environment() += environment
        }
        redirectErrorStream(redirectErrorSource)
    }.start()

    actual val pid: UInt
        // Process.pid() is not available on Android. So we try to get the pid using some hacks!
        get() = try {
            java.lang.Process::class.java.getDeclaredField("pid").run {
                isAccessible = true
                val value = getInt(process)
                isAccessible = false
                value.toUInt()
            }
        } catch (_: Exception) {
            // Just another way to get the pid. in case the reflection way failed.
            process.toString().run {
                val start = indexOf("pid=") + "pid=".length
                val end = indexOf(',', start)
                // Convert to Int first and then to UInt.
                // Maybe this is safer. Considering that the pid on unix is an Int.
                substring(start, end).trim().toInt().toUInt()
            }
        }

    actual val isAlive: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.isAlive
        } else try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }

    actual val inputSink = process.outputStream.sink()
    actual val outputSource = process.inputStream.source()
    actual val errorSource = process.errorStream.source()
    actual fun waitFor() = process.waitFor()

    actual fun destroy(force: Boolean) {
        if (force) {
            // destroyForcibly is only available from Android O onward.
            // And even thought it's available on Android O, it doesn't seem
            // to be implemented in the internal java.lang.UNIXProcess class.
            // So, we try to kill the process using Os.kill and the process internal pid.
            // If we are not able to get the pid or the Os.kill method failed,
            // we fall back to destroyForcibly or destroy. depending on the Android version.
            try {
                Os.kill(pid.toInt(), OsConstants.SIGKILL)
                closeIO()
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    process.destroyForcibly()
                } else process.destroy()
            }
        } else process.destroy()
    }
}
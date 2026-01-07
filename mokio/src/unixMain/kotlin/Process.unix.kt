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

import kotlinx.cinterop.*
import okio.Buffer
import okio.Path
import okio.Sink
import okio.Source
import platform.posix.*

actual class Process actual constructor(
    command: List<String>,
    directory: Path?,
    environment: Map<String, String>?,
    redirectErrorSource: Boolean
) {
    actual val pid: UInt

    private val inputFd: Int
    private val outputFd: Int
    private val errorFd: Int?

    init {
        memScoped {
            val childIn = allocArray<IntVar>(2).also(::pipe)
            val childOut = allocArray<IntVar>(2).also(::pipe)
            val childErr =
                if (redirectErrorSource) null
                else allocArray<IntVar>(2).also(::pipe)
            when (val forkResult = fork()) {
                0 -> {
                    close(childIn[1])
                    dup2(childIn[0], STDIN_FILENO)
                    close(childIn[0])

                    close(childOut[0])
                    dup2(childOut[1], STDOUT_FILENO)
                    close(childOut[1])

                    if (childErr == null) {
                        dup2(STDOUT_FILENO, STDERR_FILENO)
                    } else {
                        close(childErr[0])
                        dup2(childErr[1], STDERR_FILENO)
                        close(childErr[1])
                    }

                    directory?.toString()?.let(::chdir)
                    exec(command, environment)
                    throw errnoToIOException()
                }

                -1 -> throw errnoToIOException()
                else -> pid = forkResult.toUInt()
            }
            close(childIn[0])
            close(childOut[1])
            if (childErr != null) {
                close(childErr[1])
            }
            inputFd = childIn[1]
            outputFd = childOut[0]
            errorFd = childErr?.get(0)
        }
    }

    actual val isAlive: Boolean
        get() = waitpid(pid.toInt(), null, WNOHANG) == 0

    actual val inputSink: Sink = FileDescriptor(inputFd)
    actual val outputSource: Source = FileDescriptor(outputFd)
    actual val errorSource: Source =
        if (errorFd != null) FileDescriptor(errorFd)
        else Buffer()

    actual fun waitFor(): Int = memScoped {
        val status = alloc<IntVar>()
        waitpid(pid.toInt(), status.ptr, 0)
        closeIO()

        val signal = status.value and 0x7f
        return when {
            signal == 0 -> // WIFEXITED macro
                (status.value and 0xff00) shr 8 // WEXITSTATUS macro
            (signal + 1) shr 1 > 0 -> // WIFSIGNALED macro
                signal + 128 // WTERMSIG macro + 128
            else -> status.value
        }
    }

    actual fun destroy(force: Boolean) {
        kill(pid.toInt(), if (force) SIGKILL else SIGTERM)
        closeIO()
    }

    /**
     * A custom implementation of `execvpe` because it's not available on all unix platforms.
     */
    private fun MemScope.exec(
        command: List<String>,
        environment: Map<String, String>?
    ): Int {
        val file = command.first()
        val args = command.map { it.cstr.ptr }
            .plus(null)
            .toCValues()
        if (environment == null) {
            return execvp(file, args)
        }

        val env = environment
            .map { "${it.key}=${it.value}".cstr.ptr }
            .plus(null)
            .toCValues()

        fun execAsScript(): Int {
            val args = buildList(command.size + 2) {
                add(_PATH_BSHELL.cstr.ptr)
                command.forEach { add(it.cstr.ptr) }
                add(null)
            }.toCValues()
            return execve(_PATH_BSHELL, args, env)
        }

        if ('/' in file && execve(file, args, env) == -1) {
            return if (errno == ENOEXEC) execAsScript() else -1
        }

        var accessError = false
        getSystemPath().splitToSequence(':').forEach {
            execve("${it.ifEmpty { "." }}/$file", args, env)
            when (errno) {
                EACCES -> accessError = true
                EISDIR, ELOOP, ENAMETOOLONG, ENOENT, ENOTDIR, ETIMEDOUT -> {
                    // continue
                }

                ENOEXEC -> return execAsScript()
                else -> return -1
            }
        }
        if (accessError) {
            set_posix_errno(EACCES)
        }
        return -1
    }

    private fun getSystemPath(): String {
        getenv("PATH")?.toKString()?.let {
            return it
        }
        val length = confstr(_CS_PATH, null, 0u)
        if (length.toInt() == 0) {
            return "/bin:/usr/bin"
        }
        return memScoped {
            allocArray<ByteVar>(length.toLong()).apply {
                confstr(_CS_PATH, this, length)
            }.toKString()
        }
    }
}
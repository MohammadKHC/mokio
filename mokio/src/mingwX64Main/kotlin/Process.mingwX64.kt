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
import platform.windows.*

actual class Process actual constructor(
    command: List<String>,
    directory: Path?,
    environment: Map<String, String>?,
    redirectErrorSource: Boolean
) {
    actual val pid: UInt
    private val handle: HANDLE
    private val inputHandle: HANDLE
    private val outputHandle: HANDLE
    private val errorHandle: HANDLE?

    init {
        memScoped {
            val securityAttributes = alloc<SECURITY_ATTRIBUTES> {
                nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
                bInheritHandle = 1
            }

            fun createPipe(read: Boolean): Pair<HANDLE, HANDLE> {
                val readPipe = alloc<HANDLEVar>()
                val writePipe = alloc<HANDLEVar>()
                CreatePipe(
                    hReadPipe = readPipe.ptr,
                    hWritePipe = writePipe.ptr,
                    lpPipeAttributes = securityAttributes.ptr,
                    nSize = 0u
                ).ensureSuccess()

                // Ensure the opposite handle to the pipe is not inherited.
                SetHandleInformation(
                    if (read) writePipe.value else readPipe.value,
                    HANDLE_FLAG_INHERIT.toUInt(),
                    0u
                ).ensureSuccess()

                return readPipe.value!! to writePipe.value!!
            }

            val (stdinRead, stdinWrite) = createPipe(true)
            val (stdoutRead, stdoutWrite) = createPipe(false)
            val (stdErrRead, stdErrWrite) =
                if (redirectErrorSource) null to null
                else createPipe(false)

            val startupInfo = alloc<STARTUPINFO> {
                cb = sizeOf<STARTUPINFO>().toUInt()
                hStdInput = stdinRead
                hStdOutput = stdoutWrite
                hStdError = stdErrWrite ?: stdoutWrite
                dwFlags = dwFlags or STARTF_USESTDHANDLES.toUInt()
            }
            val processInfo = alloc<PROCESS_INFORMATION>()
            CreateProcessW(
                lpApplicationName = null,
                lpCommandLine = command.joinToString(" ") {
                    if (it.any(Char::isWhitespace)) {
                        "\"${it.replace("\"", "\\\"")}\""
                    } else it
                }.wcstr.ptr,
                lpProcessAttributes = null,
                lpThreadAttributes = null,
                bInheritHandles = 1,
                dwCreationFlags = CREATE_NO_WINDOW.toUInt() or CREATE_UNICODE_ENVIRONMENT.toUInt(),
                lpEnvironment = environment?.entries?.joinToString(
                    separator = Char.MIN_VALUE.toString(),
                    postfix = Char.MIN_VALUE.toString().repeat(2)
                ) { "${it.key}=${it.value}" }?.wcstr?.ptr,
                lpCurrentDirectory = directory?.toString(),
                lpStartupInfo = startupInfo.ptr,
                lpProcessInformation = processInfo.ptr
            ).ensureSuccess()

            CloseHandle(processInfo.hThread).ensureSuccess()
            pid = processInfo.dwProcessId
            handle = processInfo.hProcess!!
            CloseHandle(stdinRead).ensureSuccess()
            CloseHandle(stdoutWrite).ensureSuccess()
            stdErrWrite?.let(::CloseHandle)?.ensureSuccess()
            inputHandle = stdinWrite
            outputHandle = stdoutRead
            errorHandle = stdErrRead
        }
    }

    actual val isAlive: Boolean
        get() = WaitForSingleObject(handle, 0u).toInt() == WAIT_TIMEOUT

    actual val inputSink: Sink = FileDescriptor(inputHandle)
    actual val outputSource: Source = FileDescriptor(outputHandle)
    actual val errorSource: Source =
        if (errorHandle != null) FileDescriptor(errorHandle)
        else Buffer()

    actual fun waitFor(): Int {
        WaitForSingleObject(handle, INFINITE)
        val exitCode = memScoped {
            alloc<UIntVar> {
                GetExitCodeProcess(handle, ptr).ensureSuccess()
            }.value
        }
        close()
        return exitCode.toInt()
    }

    actual fun destroy(force: Boolean) {
        TerminateProcess(handle, 1u).ensureSuccess()
        close()
    }

    private fun close() {
        closeIO()
        CloseHandle(handle).ensureSuccess()
    }
}
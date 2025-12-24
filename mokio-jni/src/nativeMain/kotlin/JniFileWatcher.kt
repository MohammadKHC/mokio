@file:Suppress("unused")

package com.mohammedkhc.io.jni

import com.mohammedkhc.io.FileChangeEvent
import com.mohammedkhc.io.FileWatcher
import kotlinx.cinterop.*
import kotlinx.jni.*
import okio.Path.Companion.toPath

internal class JniFileWatcher(
    env: CPointer<JNIEnvVar>,
    path: jstring,
    recursive: jboolean,
    events: jobject,
    onEvent: jobject
) {
    private val javaVM = getJavaVM(env)
    private val pathClass = env.findClass("okio/Path").toGlobalRef(env)
    private val changeEvents = FileChangeEvent.entries.mapToJObjects(env)
    private val onEventRef = onEvent.toGlobalRef(env)

    private val getPathMethodId = env.getStaticMethodId(
        pathClass,
        "get",
        "(Ljava/lang/String;)Lokio/Path;"
    )
    private val invokeEventMethodId = env.getMethodId(
        env.getObjectClass(onEventRef),
        "invoke",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
    )

    private val watcher = FileWatcher(
        path.toKString(env).toPath(),
        recursive != 0.toUByte(),
        buildSet {
            val containsMethodId = env.getMethodId(
                env.getObjectClass(events),
                "contains",
                "(Ljava/lang/Object;)Z"
            )
            for ((event, jObject) in changeEvents) {
                if (env.callBooleanMethod(events, containsMethodId) {
                    alloc<jvalue> { l = jObject }.ptr
                } != 0.toUByte()) {
                    add(event)
                }
            }
        }
    ) { event, path ->
        withJniEnv(javaVM) {
            callMethod(onEventRef, invokeEventMethodId) {
                allocArray<jvalue>(2).apply {
                    get(0).l = changeEvents[event]
                    get(1).l = callStaticMethod(pathClass, getPathMethodId) {
                        alloc<jvalue> {
                            l = path.toString().toJString(this@withJniEnv, this@callMethod)
                        }.ptr
                    }
                }
            }
        }
    }

    fun startWatching() = watcher.startWatching()
    fun stopWatching(env: CPointer<JNIEnvVar>) {
        env.deleteGlobalRef(onEventRef)
        env.deleteGlobalRef(pathClass)
        changeEvents.values.forEach(env::deleteGlobalRef)
    }
}

@CName("Java_com_mohammedkhc_io_FileWatcher_init")
fun initFileWatcher(
    env: CPointer<JNIEnvVar>,
    instance: jobject,
    path: jstring,
    recursive: jboolean,
    events: jobject,
    onEvent: jobject
): Long = StableRef.create(JniFileWatcher(env, path, recursive, events, onEvent)).asCPointer().toLong()

@CName("Java_com_mohammedkhc_io_FileWatcher_startWatching")
fun startFileWatcher(env: CPointer<JNIEnvVar>, instance: jobject) =
    instance.asKStableRef<JniFileWatcher>(env).startWatching()

@CName("Java_com_mohammedkhc_io_FileWatcher_stopWatching")
fun stopFileWatcher(env: CPointer<JNIEnvVar>, instance: jobject) =
    instance.asKStableRef<JniFileWatcher>(env).stopWatching(env)
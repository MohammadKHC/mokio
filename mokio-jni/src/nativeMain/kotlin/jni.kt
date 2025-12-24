@file:OptIn(ExperimentalNativeApi::class)

package com.mohammedkhc.io.jni

import kotlinx.cinterop.*
import kotlinx.jni.*
import kotlin.enums.EnumEntries
import kotlin.experimental.ExperimentalNativeApi

internal fun CPointer<JNIEnvVar>.getMethodId(clazz: jclass, name: String, signature: String): jmethodID =
    memScoped {
        pointed.pointed!!.GetMethodID!!(this@getMethodId, clazz, name.cstr.ptr, signature.cstr.ptr)!!
    }

internal fun CPointer<JNIEnvVar>.getStaticMethodId(clazz: jclass, name: String, signature: String): jmethodID =
    memScoped {
        pointed.pointed!!.GetStaticMethodID!!(this@getStaticMethodId, clazz, name.cstr.ptr, signature.cstr.ptr)!!
    }

internal fun CPointer<JNIEnvVar>.findClass(className: String): jclass = memScoped {
    pointed.pointed!!.FindClass!!(this@findClass, className.cstr.ptr)!!
}

internal fun CPointer<JNIEnvVar>.getObjectClass(instance: jobject): jclass =
    pointed.pointed!!.GetObjectClass!!(this, instance)!!

internal fun CPointer<JNIEnvVar>.callMethod(
    instance: jobject,
    methodID: jmethodID,
    arguments: (MemScope.() -> CPointer<jvalue>)? = null
): jobject? = memScoped {
    pointed.pointed!!.CallObjectMethodA!!(this@callMethod, instance, methodID, arguments?.invoke(this))
}

internal fun CPointer<JNIEnvVar>.callBooleanMethod(
    instance: jobject,
    methodID: jmethodID,
    arguments: (MemScope.() -> CPointer<jvalue>)? = null
): jboolean = memScoped {
    pointed.pointed!!.CallBooleanMethodA!!(this@callBooleanMethod, instance, methodID, arguments?.invoke(this))
}

internal fun CPointer<JNIEnvVar>.callStaticMethod(
    clazz: jclass,
    methodID: jmethodID,
    arguments: (MemScope.() -> CPointer<jvalue>)? = null
): jobject? = memScoped {
    pointed.pointed!!.CallStaticObjectMethodA!!(this@callStaticMethod, clazz, methodID, arguments?.invoke(this))
}

internal fun jobject.toGlobalRef(env: CPointer<JNIEnvVar>): jobject =
    env.pointed.pointed!!.NewGlobalRef!!(env, this)!!

internal fun CPointer<JNIEnvVar>.deleteGlobalRef(ref: jobject) =
    pointed.pointed!!.DeleteGlobalRef!!(this, ref)

internal fun jstring.toKString(env: CPointer<JNIEnvVar>): String {
    val chars = env.pointed.pointed!!.GetStringChars!!(env, this, null)!!
    val result = chars.toKString()
    env.pointed.pointed!!.ReleaseStringChars!!(env, this, chars)
    return result
}

internal fun String.toJString(env: CPointer<JNIEnvVar>, scope: MemScope): jstring = with(scope) {
    val result = env.pointed.pointed!!.NewString!!(env, wcstr.ptr, length)!!
    defer { env.pointed.pointed!!.DeleteLocalRef!!(env, result) }
    result
}

internal fun getJavaVM(env: CPointer<JNIEnvVar>) = memScoped {
    allocPointerTo<JavaVMVar>().apply {
        env.pointed.pointed!!.GetJavaVM!!(env, ptr)
    }.value!!
}

internal fun withJniEnv(vm: CPointer<JavaVMVar>, block: CPointer<JNIEnvVar>.() -> Unit) = memScoped {
    val env = allocPointerTo<JNIEnvVar>().apply {
        vm.pointed.pointed!!.AttachCurrentThread!!(vm, ptr.reinterpret(), null)
    }.value!!
    try {
        block(env)
    } finally {
        vm.pointed.pointed!!.DetachCurrentThread!!(vm)
    }
}

internal inline fun <reified T : Any> jobject.asKStableRef(env: CPointer<JNIEnvVar>): T {
    val handleFieldId = memScoped {
        env.pointed.pointed!!.GetFieldID!!(
            env,
            env.getObjectClass(this@asKStableRef),
            "handle".cstr.ptr,
            "J".cstr.ptr
        )!!
    }
    val handle: COpaquePointer = env.pointed.pointed!!.GetLongField!!(env, this, handleFieldId).toCPointer()!!
    return handle.asStableRef<T>().get()
}

internal inline fun <reified E : Enum<E>> EnumEntries<E>.mapToJObjects(env: CPointer<JNIEnvVar>) =
    buildMap<E, jobject>(size) {
        val className = E::class.qualifiedName!!.replace(".", "/")
        val clazz = env.findClass(className)
        this@mapToJObjects.forEach {
            val fieldId = memScoped {
                env.pointed.pointed!!.GetStaticFieldID!!(env, clazz, it.name.cstr.ptr, "L$className;".cstr.ptr)!!
            }
            this[it] = env.pointed.pointed!!.GetStaticObjectField!!(env, clazz, fieldId)!!.toGlobalRef(env)
        }
    }
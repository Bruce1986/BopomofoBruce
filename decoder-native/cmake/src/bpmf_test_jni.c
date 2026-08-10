/*
 * TEST-ONLY JNI bridge for `libbpmf.so`.
 *
 * DEVPLAN W1-A explicitly excludes "JNI binding" (that's W2-A's `:decoder`
 * module: implements the `ZhuyinDecoder` Kotlin interface, thread-safety
 * wrapper, personal dictionary, DI). But W1-A's own acceptance criteria
 * requires a connectedAndroidTest that calls bpmf_init()/bpmf_input() on a
 * real device, which is impossible from Kotlin without *some* JNI glue.
 *
 * This file is that minimal glue and nothing more: four pass-through
 * functions bound to a test-only class
 * (androidTest/kotlin/.../testbridge/BpmfTestBridge.kt), named distinctly
 * (`nativeTest*` under a `testbridge` package) so it can't be mistaken for,
 * or collide with, W2-A's real production JNI binding. It is compiled into
 * the same libbpmf.so as the public bpmf_* C API for build simplicity —
 * W2-A is free to delete this file once `:decoder` has its own real
 * binding and its own connectedAndroidTest coverage.
 */

#include "bpmf.h"

#include <jni.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInit(
    JNIEnv* env, jclass clazz, jstring data_path) {
    (void)clazz;
    const char* path = (*env)->GetStringUTFChars(env, data_path, NULL);
    void* handle = bpmf_init(path);
    (*env)->ReleaseStringUTFChars(env, data_path, path);
    return (jlong)(intptr_t)handle;
}

JNIEXPORT jobjectArray JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInput(
    JNIEnv* env, jclass clazz, jlong handle, jstring zhuyin) {
    (void)clazz;
    const char* zhuyin_utf8 = (*env)->GetStringUTFChars(env, zhuyin, NULL);

    char* joined = NULL;
    size_t count = bpmf_input((void*)(intptr_t)handle, zhuyin_utf8, &joined);
    (*env)->ReleaseStringUTFChars(env, zhuyin, zhuyin_utf8);

    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, (jsize)count, string_class, NULL);

    if (count > 0 && joined != NULL) {
        char* cursor = joined;
        for (jsize i = 0; i < (jsize)count; i++) {
            char* newline = strchr(cursor, '\n');
            size_t segment_len = newline != NULL ? (size_t)(newline - cursor) : strlen(cursor);
            char* segment = (char*)malloc(segment_len + 1);
            if (segment != NULL) {
                memcpy(segment, cursor, segment_len);
                segment[segment_len] = '\0';
                jstring jsegment = (*env)->NewStringUTF(env, segment);
                (*env)->SetObjectArrayElement(env, result, i, jsegment);
                (*env)->DeleteLocalRef(env, jsegment);
                free(segment);
            }
            cursor = (newline != NULL) ? newline + 1 : cursor + segment_len;
        }
    }
    /* `joined` is owned by the bpmf handle (see bpmf.h ownership contract); do not free() it. */
    return result;
}

JNIEXPORT void JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestCommit(
    JNIEnv* env, jclass clazz, jlong handle, jint index) {
    (void)env;
    (void)clazz;
    bpmf_commit((void*)(intptr_t)handle, (size_t)index);
}

JNIEXPORT void JNICALL
Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestFree(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    bpmf_free((void*)(intptr_t)handle);
}

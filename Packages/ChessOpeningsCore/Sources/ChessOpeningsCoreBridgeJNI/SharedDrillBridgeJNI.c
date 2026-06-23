#include <stdint.h>

#if defined(__ANDROID__)
#include <jni.h>
#else
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
typedef void JNIEnv;
typedef void *jobject;
typedef void *jstring;
typedef int64_t jlong;
typedef int32_t jint;
#endif

extern int64_t chess_openings_core_shared_drill_create(const char *line_json);
extern int32_t chess_openings_core_shared_drill_submit(int64_t handle, const char *uci);
extern int32_t chess_openings_core_shared_drill_autoplay_next(int64_t handle);
extern int32_t chess_openings_core_shared_drill_ply_index(int64_t handle);
extern int32_t chess_openings_core_shared_drill_status(int64_t handle);
extern int32_t chess_openings_core_shared_drill_position_fen(int64_t handle, char *buffer, int32_t capacity);
extern int32_t chess_openings_core_shared_drill_reset(int64_t handle);
extern int32_t chess_openings_core_shared_drill_undo(int64_t handle);
extern void chess_openings_core_shared_drill_release(int64_t handle);

JNIEXPORT jlong JNICALL
Java_com_chessopenings_app_SharedCoreBridge_createSharedDrillSession(
    JNIEnv *env,
    jobject receiver,
    jstring line_json
) {
#if defined(__ANDROID__)
    (void)receiver;
    if (line_json == 0) {
        return 0;
    }

    const char *chars = (*env)->GetStringUTFChars(env, line_json, 0);
    if (chars == 0) {
        return 0;
    }
    int64_t handle = chess_openings_core_shared_drill_create(chars);
    (*env)->ReleaseStringUTFChars(env, line_json, chars);
    return (jlong)handle;
#else
    (void)env;
    (void)receiver;
    (void)line_json;
    return 0;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_submitSharedDrillMove(
    JNIEnv *env,
    jobject receiver,
    jlong handle,
    jstring uci
) {
#if defined(__ANDROID__)
    (void)receiver;
    if (uci == 0) {
        return 3;
    }

    const char *chars = (*env)->GetStringUTFChars(env, uci, 0);
    if (chars == 0) {
        return 3;
    }
    int32_t outcome = chess_openings_core_shared_drill_submit((int64_t)handle, chars);
    (*env)->ReleaseStringUTFChars(env, uci, chars);
    return (jint)outcome;
#else
    (void)env;
    (void)receiver;
    (void)handle;
    (void)uci;
    return -1;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_autoplaySharedDrillNext(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_autoplay_next((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedDrillPlyIndex(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_ply_index((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedDrillStatus(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_status((int64_t)handle);
}

JNIEXPORT jstring JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedDrillPositionFen(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
#if defined(__ANDROID__)
    (void)receiver;
    char buffer[256];
    int32_t length = chess_openings_core_shared_drill_position_fen(
        (int64_t)handle,
        buffer,
        (int32_t)sizeof(buffer)
    );
    if (length < 0) {
        return 0;
    }
    return (*env)->NewStringUTF(env, buffer);
#else
    (void)env;
    (void)receiver;
    (void)handle;
    return 0;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_undoSharedDrillSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_undo((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_resetSharedDrillSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_reset((int64_t)handle);
}

JNIEXPORT void JNICALL
Java_com_chessopenings_app_SharedCoreBridge_releaseSharedDrillSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    chess_openings_core_shared_drill_release((int64_t)handle);
}

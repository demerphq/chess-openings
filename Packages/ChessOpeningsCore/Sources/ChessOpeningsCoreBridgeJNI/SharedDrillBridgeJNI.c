#include <stdint.h>
#include <stdlib.h>

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
extern int32_t chess_openings_core_shared_drill_restore(int64_t handle, int32_t ply_index, int32_t user_side);
extern int32_t chess_openings_core_shared_drill_undo(int64_t handle);
extern void chess_openings_core_shared_drill_release(int64_t handle);
extern int64_t chess_openings_core_shared_playout_create(const char *starting_fen, int32_t user_side, int32_t engine_skill);
extern int32_t chess_openings_core_shared_playout_bootstrap(int64_t handle);
extern int32_t chess_openings_core_shared_playout_submit(int64_t handle, const char *uci);
extern int32_t chess_openings_core_shared_playout_ply_index(int64_t handle);
extern int32_t chess_openings_core_shared_playout_status(int64_t handle);
extern int32_t chess_openings_core_shared_playout_position_fen(int64_t handle, char *buffer, int32_t capacity);
extern int32_t chess_openings_core_shared_playout_moves_json(int64_t handle, char *buffer, int32_t capacity);
extern int32_t chess_openings_core_shared_playout_restore_moves(int64_t handle, const char *moves_json);
extern int32_t chess_openings_core_shared_playout_offer_draw(int64_t handle);
extern int32_t chess_openings_core_shared_playout_resign(int64_t handle);
extern int32_t chess_openings_core_shared_playout_undo(int64_t handle);
extern void chess_openings_core_shared_playout_release(int64_t handle);
extern void chess_openings_core_shared_engine_configure(const char *executable_path, const char *nnue_directory);

JNIEXPORT void JNICALL
Java_com_chessopenings_app_SharedCoreBridge_configureSharedEngine(
    JNIEnv *env,
    jobject receiver,
    jstring executable_path,
    jstring nnue_directory
) {
#if defined(__ANDROID__)
    (void)receiver;
    const char *executable_chars = 0;
    const char *nnue_chars = 0;

    if (executable_path != 0) {
        executable_chars = (*env)->GetStringUTFChars(env, executable_path, 0);
    }
    if (nnue_directory != 0) {
        nnue_chars = (*env)->GetStringUTFChars(env, nnue_directory, 0);
    }

    chess_openings_core_shared_engine_configure(executable_chars, nnue_chars);

    if (executable_chars != 0) {
        (*env)->ReleaseStringUTFChars(env, executable_path, executable_chars);
    }
    if (nnue_chars != 0) {
        (*env)->ReleaseStringUTFChars(env, nnue_directory, nnue_chars);
    }
#else
    (void)env;
    (void)receiver;
    (void)executable_path;
    (void)nnue_directory;
#endif
}

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

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_restoreSharedDrillSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle,
    jint ply_index,
    jint user_side
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_drill_restore(
        (int64_t)handle,
        (int32_t)ply_index,
        (int32_t)user_side
    );
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

JNIEXPORT jlong JNICALL
Java_com_chessopenings_app_SharedCoreBridge_createSharedPlayoutSession(
    JNIEnv *env,
    jobject receiver,
    jstring starting_fen,
    jint user_side,
    jint engine_skill
) {
#if defined(__ANDROID__)
    (void)receiver;
    if (starting_fen == 0) {
        return 0;
    }

    const char *chars = (*env)->GetStringUTFChars(env, starting_fen, 0);
    if (chars == 0) {
        return 0;
    }
    int64_t handle = chess_openings_core_shared_playout_create(
        chars,
        (int32_t)user_side,
        (int32_t)engine_skill
    );
    (*env)->ReleaseStringUTFChars(env, starting_fen, chars);
    return (jlong)handle;
#else
    (void)env;
    (void)receiver;
    (void)starting_fen;
    (void)user_side;
    (void)engine_skill;
    return 0;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_bootstrapSharedPlayoutSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_bootstrap((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_submitSharedPlayoutMove(
    JNIEnv *env,
    jobject receiver,
    jlong handle,
    jstring uci
) {
#if defined(__ANDROID__)
    (void)receiver;
    if (uci == 0) {
        return 2;
    }

    const char *chars = (*env)->GetStringUTFChars(env, uci, 0);
    if (chars == 0) {
        return 2;
    }
    int32_t outcome = chess_openings_core_shared_playout_submit((int64_t)handle, chars);
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
Java_com_chessopenings_app_SharedCoreBridge_sharedPlayoutPlyIndex(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_ply_index((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedPlayoutStatus(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_status((int64_t)handle);
}

JNIEXPORT jstring JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedPlayoutPositionFen(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
#if defined(__ANDROID__)
    (void)receiver;
    char buffer[256];
    int32_t length = chess_openings_core_shared_playout_position_fen(
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

JNIEXPORT jstring JNICALL
Java_com_chessopenings_app_SharedCoreBridge_sharedPlayoutMovesJson(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
#if defined(__ANDROID__)
    (void)receiver;
    int32_t length = chess_openings_core_shared_playout_moves_json(
        (int64_t)handle,
        0,
        0
    );
    if (length < 0) {
        return 0;
    }
    char *buffer = (char *)malloc((size_t)length + 1);
    if (buffer == 0) {
        return 0;
    }
    int32_t written = chess_openings_core_shared_playout_moves_json(
        (int64_t)handle,
        buffer,
        length + 1
    );
    if (written < 0) {
        free(buffer);
        return 0;
    }
    jstring result = (*env)->NewStringUTF(env, buffer);
    free(buffer);
    return result;
#else
    (void)env;
    (void)receiver;
    (void)handle;
    return 0;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_restoreSharedPlayoutMoves(
    JNIEnv *env,
    jobject receiver,
    jlong handle,
    jstring moves_json
) {
#if defined(__ANDROID__)
    (void)receiver;
    if (moves_json == 0) {
        return -1;
    }

    const char *chars = (*env)->GetStringUTFChars(env, moves_json, 0);
    if (chars == 0) {
        return -1;
    }
    int32_t restored = chess_openings_core_shared_playout_restore_moves((int64_t)handle, chars);
    (*env)->ReleaseStringUTFChars(env, moves_json, chars);
    return (jint)restored;
#else
    (void)env;
    (void)receiver;
    (void)handle;
    (void)moves_json;
    return -1;
#endif
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_offerSharedPlayoutDraw(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_offer_draw((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_resignSharedPlayout(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_resign((int64_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_chessopenings_app_SharedCoreBridge_undoSharedPlayoutSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    return (jint)chess_openings_core_shared_playout_undo((int64_t)handle);
}

JNIEXPORT void JNICALL
Java_com_chessopenings_app_SharedCoreBridge_releaseSharedPlayoutSession(
    JNIEnv *env,
    jobject receiver,
    jlong handle
) {
    (void)env;
    (void)receiver;
    chess_openings_core_shared_playout_release((int64_t)handle);
}

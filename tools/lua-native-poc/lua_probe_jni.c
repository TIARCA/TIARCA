/*
 * Android JNI link smoke test only. This file is not referenced from TIARCA's
 * manifest or Java app; the .so is not packaged into either production flavor.
 * DO NOT call it from the main IRC process. A separate isolated Android
 * Service and IPC checks are mandatory before exposing user scripts.
 */
#include <jni.h>
#include <stdio.h>
#include <string.h>

#include "lua_sandbox.h"

JNIEXPORT jstring JNICALL
Java_io_tiarca_irc_luapoc_LuaProbe_runProbe(JNIEnv *env, jclass type, jstring script) {
    (void)type;
    if (script == NULL) {
        return (*env)->NewStringUTF(env, "invalid probe argument");
    }
    const jsize len = (*env)->GetStringUTFLength(env, script);
    if (len > 8192) {
        return (*env)->NewStringUTF(env, "script too large for probe");
    }
    const char *contents = (*env)->GetStringUTFChars(env, script, NULL);
    if (contents == NULL) {
        return NULL;
    }

    char output[256];
    int status = tiarca_lua_poc_run(contents, (size_t)len,
                                    2u * 1024u * 1024u, 200000ul,
                                    output, sizeof(output));
    (*env)->ReleaseStringUTFChars(env, script, contents);

    char response[280];
    snprintf(response, sizeof(response), "%s: %s", status == 0 ? "OK" : "ERROR", output);
    return (*env)->NewStringUTF(env, response);
}

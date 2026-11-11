/*
 * TIARCA Lua 5.5.1 scripting MVP. Confined to an Android isolatedProcess service.
 * This is not an OS sandbox: Android's isolated UID remains the security boundary.
 */
#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

#define MAX_SCRIPTS 8
#define MAX_HANDLERS 8
#define MAX_ACTIONS 12
#define MAX_SOURCE 8192
#define SCRIPT_MEMORY (2u * 1024u * 1024u)
#define INSTRUCTION_LIMIT 200000ul

typedef struct {
    char kind[8];
    char target[97];
    char value[513];
} ScriptAction;

typedef struct ScriptVm {
    char id[40];
    lua_State *L;
    size_t allocated;
    unsigned long steps;
    int callbacks[3][MAX_HANDLERS];
    int count[3];
    int event_active;
    int action_count;
    ScriptAction actions[MAX_ACTIONS];
} ScriptVm;

static ScriptVm *g_scripts[MAX_SCRIPTS]; /* All calls arrive on one service HandlerThread. */

static ScriptVm *context(lua_State *L) {
    return *(ScriptVm **) lua_getextraspace(L);
}

static void *bounded_alloc(void *ud, void *ptr, size_t old, size_t new_size) {
    ScriptVm *s = (ScriptVm *) ud;
    size_t old_size = ptr == NULL ? 0 : old;
    if (new_size == 0) {
        free(ptr);
        s->allocated -= old_size <= s->allocated ? old_size : s->allocated;
        return NULL;
    }
    if (old_size > s->allocated) return NULL;
    if (new_size > old_size && new_size - old_size > SCRIPT_MEMORY - s->allocated)
        return NULL;
    void *p = realloc(ptr, new_size);
    if (p != NULL) s->allocated = s->allocated - old_size + new_size;
    return p;
}

static void budget_hook(lua_State *L, lua_Debug *unused) {
    (void)unused;
    ScriptVm *s = context(L);
    if (s->steps + 100 > INSTRUCTION_LIMIT)
        luaL_error(L, "TIARCA Lua: instruction budget exceeded");
    s->steps += 100;
}

static int event_kind(const char *name) {
    if (!strcmp(name, "message")) return 0;
    if (!strcmp(name, "connected")) return 1;
    if (!strcmp(name, "disconnected")) return 2;
    return -1;
}

static int register_handler(lua_State *L) {
    ScriptVm *s = context(L);
    const char *event_name = luaL_checkstring(L, 1);
    luaL_checktype(L, 2, LUA_TFUNCTION);
    int kind = event_kind(event_name);
    if (s->event_active || kind < 0)
        return luaL_error(L, "tiarca.on: unsupported event or late registration");
    if (s->count[kind] >= MAX_HANDLERS)
        return luaL_error(L, "tiarca.on: too many callbacks");
    lua_pushvalue(L, 2);
    s->callbacks[kind][s->count[kind]++] = luaL_ref(L, LUA_REGISTRYINDEX);
    return 0;
}

static void add_action(lua_State *L, const char *kind, const char *target,
                       size_t target_size, const char *value, size_t value_size) {
    ScriptVm *s = context(L);
    if (!s->event_active) luaL_error(L, "tiarca: action outside event handler");
    if (s->action_count >= MAX_ACTIONS) luaL_error(L, "tiarca: too many actions");
    if (target_size > 96 || value_size == 0 || value_size > 512)
        luaL_error(L, "tiarca: invalid action size");
    if (memchr(target, 0, target_size) || memchr(value, 0, value_size))
        luaL_error(L, "tiarca: NUL forbidden");
    ScriptAction *a = &s->actions[s->action_count++];
    strncpy(a->kind, kind, sizeof(a->kind) - 1);
    memcpy(a->target, target, target_size);
    a->target[target_size] = 0;
    memcpy(a->value, value, value_size);
    a->value[value_size] = 0;
}

static int script_log(lua_State *L) {
    size_t len;
    const char *value = luaL_checklstring(L, 1, &len);
    if (len > 512) return luaL_error(L, "tiarca.log: message too long");
    add_action(L, "log", "", 0, value, len);
    return 0;
}

static int script_send(lua_State *L) {
    size_t target_len, message_len;
    const char *target = luaL_checklstring(L, 1, &target_len);
    const char *message = luaL_checklstring(L, 2, &message_len);
    add_action(L, "send", target, target_len, message, message_len);
    return 0;
}

static void install_libraries(lua_State *L) {
    const struct { const char *name; lua_CFunction fn; } permitted[] = {
        {LUA_GNAME, luaopen_base}, {LUA_TABLIBNAME, luaopen_table},
        {LUA_STRLIBNAME, luaopen_string}, {LUA_MATHLIBNAME, luaopen_math},
        {LUA_UTF8LIBNAME, luaopen_utf8}
    };
    for (size_t i = 0; i < sizeof(permitted) / sizeof(permitted[0]); i++) {
        luaL_requiref(L, permitted[i].name, permitted[i].fn, 1);
        lua_pop(L, 1);
    }
    const char *blocked[] = {
        "dofile", "loadfile", "load", "collectgarbage", "print", "warn", "require"
    };
    for (size_t i = 0; i < sizeof(blocked)/sizeof(blocked[0]); i++) {
        lua_pushnil(L);
        lua_setglobal(L, blocked[i]);
    }
    lua_getglobal(L, "string");
    lua_pushnil(L);
    lua_setfield(L, -2, "dump");
    lua_pop(L, 1);
    lua_newtable(L);
    lua_pushcfunction(L, register_handler);
    lua_setfield(L, -2, "on");
    lua_pushcfunction(L, script_log);
    lua_setfield(L, -2, "log");
    lua_pushcfunction(L, script_send);
    lua_setfield(L, -2, "send");
    lua_setglobal(L, "tiarca");
}

static void destroy(ScriptVm *s) {
    if (!s) return;
    if (s->L) lua_close(s->L);
    free(s);
}

static int slot_for(const char *id) {
    for (int i = 0; i < MAX_SCRIPTS; i++)
        if (g_scripts[i] && strcmp(g_scripts[i]->id, id) == 0) return i;
    return -1;
}

static jbyteArray jbytes(JNIEnv *env, const char *s, size_t n) {
    if (n > INT_MAX) return NULL;
    jbyteArray ret = (*env)->NewByteArray(env, (jsize)n);
    if (ret && n != 0) (*env)->SetByteArrayRegion(env, ret, 0, (jsize)n, (const jbyte *)s);
    return ret;
}

static jbyteArray jmessage(JNIEnv *env, const char *s) {
    return jbytes(env, s, strlen(s));
}

JNIEXPORT jbyteArray JNICALL
Java_io_mrarm_irc_lua_NativeLuaRuntime_load(JNIEnv *env, jclass unused,
                                             jstring jid, jbyteArray jsource) {
    (void)unused;
    if (!jid || !jsource || (*env)->GetArrayLength(env, jsource) > MAX_SOURCE)
        return jmessage(env, "ERROR: invalid script");
    const char *id = (*env)->GetStringUTFChars(env, jid, NULL);
    if (!id) return NULL;
    size_t id_len = strlen(id);
    if (id_len != 36) {
        (*env)->ReleaseStringUTFChars(env, jid, id);
        return jmessage(env, "ERROR: invalid id");
    }
    int slot = slot_for(id);
    if (slot >= 0) {
        destroy(g_scripts[slot]);
        g_scripts[slot] = NULL;
    } else {
        for (int i = 0; i < MAX_SCRIPTS; i++)
            if (!g_scripts[i]) { slot = i; break; }
    }
    if (slot < 0) {
        (*env)->ReleaseStringUTFChars(env, jid, id);
        return jmessage(env, "ERROR: too many scripts");
    }

    ScriptVm *s = (ScriptVm *) calloc(1, sizeof(ScriptVm));
    if (!s) {
        (*env)->ReleaseStringUTFChars(env, jid, id);
        return jmessage(env, "ERROR: out of memory");
    }
    memcpy(s->id, id, id_len + 1);
    (*env)->ReleaseStringUTFChars(env, jid, id);
    s->L = lua_newstate(bounded_alloc, s, luaL_makeseed(NULL));
    if (!s->L) {
        destroy(s);
        return jmessage(env, "ERROR: Lua allocation failed");
    }
    *(ScriptVm **)lua_getextraspace(s->L) = s;
    install_libraries(s->L);
    lua_sethook(s->L, budget_hook, LUA_MASKCOUNT, 100);

    jsize n = (*env)->GetArrayLength(env, jsource);
    jbyte *source = (*env)->GetByteArrayElements(env, jsource, NULL);
    if (!source) { destroy(s); return NULL; }
    int code = luaL_loadbufferx(s->L, (const char *)source, (size_t)n, "tiarca-script", "t");
    (*env)->ReleaseByteArrayElements(env, jsource, source, JNI_ABORT);
    if (code == LUA_OK) code = lua_pcall(s->L, 0, 0, 0);

    char result[320] = "OK";
    if (code != LUA_OK) {
        const char *error = lua_tostring(s->L, -1);
        snprintf(result, sizeof(result), "ERROR: %.300s",
                 error ? error : "Lua initialization error");
        destroy(s);
    } else {
        lua_sethook(s->L, NULL, 0, 0);
        g_scripts[slot] = s;
    }
    return jmessage(env, result);
}

JNIEXPORT void JNICALL
Java_io_mrarm_irc_lua_NativeLuaRuntime_unload(JNIEnv *env, jclass unused, jstring jid) {
    (void)unused;
    if (!jid) return;
    const char *id = (*env)->GetStringUTFChars(env, jid, NULL);
    if (!id) return;
    int slot = slot_for(id);
    if (slot >= 0) { destroy(g_scripts[slot]); g_scripts[slot] = NULL; }
    (*env)->ReleaseStringUTFChars(env, jid, id);
}

JNIEXPORT void JNICALL
Java_io_mrarm_irc_lua_NativeLuaRuntime_clear(JNIEnv *env, jclass unused) {
    (void)env; (void)unused;
    for (int i = 0; i < MAX_SCRIPTS; i++) {
        destroy(g_scripts[i]); g_scripts[i] = NULL;
    }
}

static int copy_java_bytes(JNIEnv *env, jbyteArray array, char *dst, size_t cap) {
    if (!array) { dst[0] = 0; return 1; }
    jsize n = (*env)->GetArrayLength(env, array);
    if (n < 0 || (size_t)n >= cap) return 0;
    (*env)->GetByteArrayRegion(env, array, 0, n, (jbyte*)dst);
    dst[n] = 0;
    if (memchr(dst, 0, n)) return 0;
    return 1;
}

static void set_field(lua_State *L, const char *name, const char *value) {
    lua_pushstring(L, value);
    lua_setfield(L, -2, name);
}

JNIEXPORT jobjectArray JNICALL
Java_io_mrarm_irc_lua_NativeLuaRuntime_dispatch(JNIEnv *env, jclass unused,
        jstring jid, jstring jevent, jbyteArray jserver, jbyteArray jchannel,
        jbyteArray jnick, jbyteArray jtext, jbyteArray jtype, jlong timestamp) {
    (void)unused;
    char server[64], channel[97], nick[97], text[2049], type[32];
    if (!jid || !jevent ||
        !copy_java_bytes(env, jserver, server, sizeof(server)) ||
        !copy_java_bytes(env, jchannel, channel, sizeof(channel)) ||
        !copy_java_bytes(env, jnick, nick, sizeof(nick)) ||
        !copy_java_bytes(env, jtext, text, sizeof(text)) ||
        !copy_java_bytes(env, jtype, type, sizeof(type)))
        return NULL;
    const char *id = (*env)->GetStringUTFChars(env, jid, NULL);
    const char *event_name = (*env)->GetStringUTFChars(env, jevent, NULL);
    if (!id || !event_name) {
        if (id) (*env)->ReleaseStringUTFChars(env, jid, id);
        if (event_name) (*env)->ReleaseStringUTFChars(env, jevent, event_name);
        return NULL;
    }
    int slot = slot_for(id);
    int kind = event_kind(event_name);
    (*env)->ReleaseStringUTFChars(env, jid, id);
    (*env)->ReleaseStringUTFChars(env, jevent, event_name);
    if (slot < 0 || kind < 0) return NULL;
    ScriptVm *s = g_scripts[slot];
    lua_State *L = s->L;
    s->steps = 0;
    s->action_count = 0;
    s->event_active = 1;
    lua_sethook(L, budget_hook, LUA_MASKCOUNT, 100);

    char error[320] = "OK";
    for (int index = 0; index < s->count[kind]; index++) {
        lua_rawgeti(L, LUA_REGISTRYINDEX, s->callbacks[kind][index]);
        lua_newtable(L);
        set_field(L, "server", server);
        set_field(L, "channel", channel);
        set_field(L, "nick", nick);
        set_field(L, "text", text);
        set_field(L, "message_type", type);
        const char *event_names[] = {"message", "connected", "disconnected"};
        set_field(L, "type", event_names[kind]);
        lua_pushinteger(L, (lua_Integer)timestamp);
        lua_setfield(L, -2, "timestamp");
        if (lua_pcall(L, 1, 0, 0) != LUA_OK) {
            const char *message = lua_tostring(L, -1);
            snprintf(error, sizeof(error), "ERROR: %.300s",
                     message ? message : "Lua callback error");
            lua_pop(L, 1);
            s->action_count = 0; /* discard every action on callback failure */
            break;
        }
    }
    s->event_active = 0;
    lua_sethook(L, NULL, 0, 0);
    int n = s->action_count;
    jclass byteArrayType = (*env)->FindClass(env, "[B");
    if (!byteArrayType) return NULL;
    jobjectArray out = (*env)->NewObjectArray(env, 1 + 3 * n, byteArrayType, NULL);
    if (!out) return NULL;
    jbyteArray status = jmessage(env, error);
    (*env)->SetObjectArrayElement(env, out, 0, status);
    (*env)->DeleteLocalRef(env, status);
    for (int i = 0; i < n; i++) {
        const char *fields[] = {
            s->actions[i].kind, s->actions[i].target, s->actions[i].value
        };
        for (int j = 0; j < 3; j++) {
            jbyteArray value = jmessage(env, fields[j]);
            if (!value) return NULL;
            (*env)->SetObjectArrayElement(env, out, 1 + 3 * i + j, value);
            (*env)->DeleteLocalRef(env, value);
        }
    }
    return out;
}

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
#define MAX_CHANNELS 32
#define MAX_USERS 128
#define EVENT_KINDS 9
#define MAX_SOURCE 8192
#define SCRIPT_MEMORY (2u * 1024u * 1024u)
#define INSTRUCTION_LIMIT 200000ul

typedef struct {
    char kind[8];
    char target[97];
    char value[513];
} ScriptAction;

typedef struct {
    char name[97];
    int users;
} ChannelSnapshot;

typedef struct {
    char nick[97];
    char prefixes[33];
    char ident[97];
    char host[257];
    char account[97];
} UserSnapshot;

typedef struct ScriptVm {
    char id[40];
    lua_State *L;
    size_t allocated;
    unsigned long steps;
    int callbacks[EVENT_KINDS][MAX_HANDLERS];
    int count[EVENT_KINDS];
    int event_active;
    int action_count;
    ScriptAction actions[MAX_ACTIONS];
    ChannelSnapshot channels[MAX_CHANNELS];
    int channel_count;
    int channels_truncated;
    UserSnapshot users[MAX_USERS];
    int user_count;
    int users_truncated;
    int can_read_users;
    char event_channel[97];
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
    if (!strcmp(name, "command")) return 3;
    if (!strcmp(name, "join")) return 4;
    if (!strcmp(name, "part")) return 5;
    if (!strcmp(name, "quit")) return 6;
    if (!strcmp(name, "nick")) return 7;
    if (!strcmp(name, "timer")) return 8;
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

/* No WHO request, socket or private-tab access; event-scoped cached data. */
static int script_get_channels(lua_State *L) {
    ScriptVm *s = context(L);
    if (!s->event_active)
        return luaL_error(L, "tiarca.get_channels: only available in a callback");
    lua_createtable(L, s->channel_count, 1);
    for (int i = 0; i < s->channel_count; i++) {
        lua_createtable(L, 0, 2);
        lua_pushstring(L, s->channels[i].name);
        lua_setfield(L, -2, "name");
        lua_pushinteger(L, s->channels[i].users);
        lua_setfield(L, -2, "users");
        lua_rawseti(L, -2, i + 1);
    }
    lua_pushboolean(L, s->channels_truncated);
    lua_setfield(L, -2, "truncated");
    return 1;
}

static void set_field(lua_State *L, const char *name, const char *value);

/* Roster is limited to the event's joined channel and a separate user consent. */
static int script_get_users(lua_State *L) {
    ScriptVm *s = context(L);
    const char *channel = luaL_checkstring(L, 1);
    if (!s->event_active || !s->can_read_users ||
            strcmp(channel, s->event_channel) != 0 || !channel[0])
        return luaL_error(L, "tiarca.get_users: unavailable or not authorized");
    lua_createtable(L, s->user_count, 1);
    for (int i = 0; i < s->user_count; i++) {
        const UserSnapshot *u = &s->users[i];
        lua_createtable(L, 0, 6);
        lua_pushstring(L, u->nick);
        lua_setfield(L, -2, "nick");
        lua_pushstring(L, u->prefixes);
        lua_setfield(L, -2, "prefix");
        if (u->ident[0]) set_field(L, "ident", u->ident);
        if (u->host[0]) set_field(L, "host", u->host);
        if (u->account[0]) set_field(L, "account", u->account);
        if (u->ident[0] && u->host[0]) {
            lua_pushfstring(L, "%s!%s@%s", u->nick, u->ident, u->host);
            lua_setfield(L, -2, "mask");
        }
        lua_rawseti(L, -2, i + 1);
    }
    lua_pushboolean(L, s->users_truncated);
    lua_setfield(L, -2, "truncated");
    return 1;
}

static void channel_action(lua_State *L, const char *kind,
                           const char *value, size_t value_size) {
    size_t channel_size;
    const char *channel = luaL_checklstring(L, 1, &channel_size);
    add_action(L, kind, channel, channel_size, value, value_size);
}

static int script_refresh_users(lua_State *L) {
    channel_action(L, "who", "refresh", 7);
    return 0;
}

static int script_ban(lua_State *L) {
    size_t n;
    const char *nick = luaL_checklstring(L, 2, &n);
    if (n == 0 || n > 64) return luaL_error(L, "tiarca.ban: invalid nick");
    channel_action(L, "ban", nick, n);
    return 0;
}

static int script_mute(lua_State *L) {
    size_t n;
    const char *nick = luaL_checklstring(L, 2, &n);
    if (n == 0 || n > 64) return luaL_error(L, "tiarca.mute: invalid nick");
    channel_action(L, "mute", nick, n);
    return 0;
}

static int script_tban(lua_State *L) {
    size_t n, duration_len;
    const char *nick = luaL_checklstring(L, 2, &n);
    const char *duration = luaL_checklstring(L, 3, &duration_len);
    if (n == 0 || n > 64 || duration_len == 0 || duration_len > 4 ||
            memchr(nick, '\t', n) || memchr(duration, '\t', duration_len))
        return luaL_error(L, "tiarca.tban: invalid nick or duration");
    char payload[72];
    memcpy(payload, nick, n);
    payload[n] = '\t';
    memcpy(payload + n + 1, duration, duration_len);
    channel_action(L, "tban", payload, n + 1 + duration_len);
    return 0;
}

static int script_kick(lua_State *L) {
    size_t n, reason_len = 0;
    const char *nick = luaL_checklstring(L, 2, &n);
    const char *reason = luaL_optlstring(L, 3, "Removed by operator script", &reason_len);
    if (n == 0 || n > 64 || reason_len > 160 ||
            memchr(nick, '\t', n) || memchr(reason, '\t', reason_len))
        return luaL_error(L, "tiarca.kick: invalid nick or reason");
    char payload[228];
    memcpy(payload, nick, n);
    payload[n] = '\t';
    memcpy(payload + n + 1, reason, reason_len);
    channel_action(L, "kick", payload, n + 1 + reason_len);
    return 0;
}

/* Only standard user modes, never arbitrary /MODE or raw IRC. */
static int script_mode(lua_State *L) {
    size_t n, m;
    const char *nick = luaL_checklstring(L, 2, &n);
    const char *mode = luaL_checklstring(L, 3, &m);
    if (n == 0 || n > 64 || m != 2 ||
            !(mode[0] == '+' || mode[0] == '-') ||
            !(mode[1] == 'o' || mode[1] == 'v') ||
            memchr(nick, '\t', n))
        return luaL_error(L, "tiarca.mode: only +o -o +v -v");
    char payload[68];
    memcpy(payload, nick, n);
    payload[n] = '\t';
    memcpy(payload + n + 1, mode, 2);
    channel_action(L, "mode", payload, n + 3);
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
    lua_pushcfunction(L, script_get_channels);
    lua_setfield(L, -2, "get_channels");
    lua_pushcfunction(L, script_get_users);
    lua_setfield(L, -2, "get_users");
    lua_pushcfunction(L, script_refresh_users);
    lua_setfield(L, -2, "refresh_users");
    lua_pushcfunction(L, script_kick);
    lua_setfield(L, -2, "kick");
    lua_pushcfunction(L, script_ban);
    lua_setfield(L, -2, "ban");
    lua_pushcfunction(L, script_mute);
    lua_setfield(L, -2, "mute");
    lua_pushcfunction(L, script_tban);
    lua_setfield(L, -2, "tban");
    lua_pushcfunction(L, script_mode);
    lua_setfield(L, -2, "mode");
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
        jbyteArray jnick, jbyteArray jtext, jbyteArray jtype, jlong timestamp,
        jobjectArray jchannels, jintArray jcounts, jboolean jtruncated,
        jobjectArray jroster, jboolean jrostertruncated,
        jboolean jcanreadusers) {
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
    if (!jchannels || !jcounts) return NULL;
    jsize count = (*env)->GetArrayLength(env, jchannels);
    if (count < 0 || count > MAX_CHANNELS ||
            count != (*env)->GetArrayLength(env, jcounts)) return NULL;
    jint users[MAX_CHANNELS] = {0};
    if (count > 0) (*env)->GetIntArrayRegion(env, jcounts, 0, count, users);
    if ((*env)->ExceptionCheck(env)) return NULL;
    s->channel_count = 0;
    s->channels_truncated = jtruncated == JNI_TRUE;
    for (jsize i = 0; i < count; i++) {
        jbyteArray name = (jbyteArray)(*env)->GetObjectArrayElement(env, jchannels, i);
        if (!name) return NULL;
        int valid = copy_java_bytes(env, name, s->channels[i].name,
                                    sizeof(s->channels[i].name));
        (*env)->DeleteLocalRef(env, name);
        if (!valid || s->channels[i].name[0] == 0 || users[i] < 0) return NULL;
        s->channels[i].users = users[i];
        s->channel_count++;
    }
    if (!jroster) return NULL;
    jsize fields = (*env)->GetArrayLength(env, jroster);
    if (fields < 0 || fields > MAX_USERS * 5 || fields % 5 != 0)
        return NULL;
    s->user_count = 0;
    s->users_truncated = jrostertruncated == JNI_TRUE;
    s->can_read_users = jcanreadusers == JNI_TRUE;
    memcpy(s->event_channel, channel, sizeof(channel));
    const size_t limits[5] = {sizeof(s->users[0].nick),
                              sizeof(s->users[0].prefixes),
                              sizeof(s->users[0].ident),
                              sizeof(s->users[0].host),
                              sizeof(s->users[0].account)};
    for (jsize index = 0; index < fields; index++) {
        jbyteArray field = (jbyteArray)(*env)->GetObjectArrayElement(env, jroster, index);
        if (!field) return NULL;
        UserSnapshot *entry = &s->users[index / 5];
        char *destinations[] = {entry->nick, entry->prefixes, entry->ident,
                                entry->host, entry->account};
        int ok = copy_java_bytes(env, field, destinations[index % 5],
                                 limits[index % 5]);
        (*env)->DeleteLocalRef(env, field);
        if (!ok) return NULL;
        if (index % 5 == 0) {
            if (!entry->nick[0]) return NULL;
            s->user_count++;
        }
    }
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
        const char *event_names[] = {"message", "connected", "disconnected", "command",
                                    "join", "part", "quit", "nick", "timer"};
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

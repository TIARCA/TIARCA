/*
 * Proof-of-concept ONLY. Keep Lua out of the app process until the Android
 * isolated-service IPC and crash/timeout tests have been implemented.
 * The allocator and instruction hook are defense in depth, not a sandbox
 * equivalent to a separate OS UID.
 */
#include "lua_sandbox.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

typedef struct {
    size_t used;
    size_t limit;
    unsigned long instructions;
    unsigned long instruction_limit;
} ProbeBudget;

static void *limited_alloc(void *userdata, void *ptr, size_t old_size, size_t new_size) {
    ProbeBudget *budget = (ProbeBudget *)userdata;
    const size_t actual_old = ptr == NULL ? 0 : old_size;

    if (new_size == 0) {
        free(ptr);
        budget->used -= actual_old <= budget->used ? actual_old : budget->used;
        return NULL;
    }
    if (actual_old > budget->used) {
        return NULL;
    }
    if (new_size > actual_old && new_size - actual_old > budget->limit - budget->used) {
        return NULL;
    }

    void *replacement = realloc(ptr, new_size);
    if (replacement != NULL) {
        budget->used = budget->used - actual_old + new_size;
    }
    return replacement;
}

static void instruction_hook(lua_State *state, lua_Debug *debug_info) {
    (void)debug_info;
    ProbeBudget *budget = *(ProbeBudget **)lua_getextraspace(state);
    if (budget == NULL || budget->instruction_limit - budget->instructions <= 100) {
        luaL_error(state, "TIARCA Lua proof: instruction budget exceeded");
    }
    budget->instructions += 100;
}

static void remove_global(lua_State *state, const char *name) {
    lua_pushnil(state);
    lua_setglobal(state, name);
}

static void allow_library(lua_State *state, const char *name, lua_CFunction opener) {
    luaL_requiref(state, name, opener, 1);
    lua_pop(state, 1);
}

int tiarca_lua_poc_run(const char *script, size_t script_size,
                       size_t memory_budget_bytes, unsigned long instruction_budget,
                       char *output, size_t output_size) {
    if (output == NULL || output_size == 0) {
        return 1;
    }
    output[0] = '\0';
    if (script == NULL || script_size > 8192 || memory_budget_bytes < 65536 ||
        instruction_budget < 100 || instruction_budget > 10000000) {
        snprintf(output, output_size, "invalid probe arguments");
        return 1;
    }

    ProbeBudget budget = {0, memory_budget_bytes, 0, instruction_budget};
    lua_State *state = lua_newstate(limited_alloc, &budget);
    if (state == NULL) {
        snprintf(output, output_size, "failed to create Lua state (memory budget)");
        return 1;
    }

    /* No luaL_openlibs: never open io, os, package, debug or Java bindings. */
    allow_library(state, LUA_GNAME, luaopen_base);
    allow_library(state, LUA_TABLIBNAME, luaopen_table);
    allow_library(state, LUA_STRLIBNAME, luaopen_string);
    allow_library(state, LUA_MATHLIBNAME, luaopen_math);
    allow_library(state, LUA_UTF8LIBNAME, luaopen_utf8);
    remove_global(state, "dofile");
    remove_global(state, "loadfile");
    remove_global(state, "load");
    remove_global(state, "collectgarbage");
    remove_global(state, "print");
    remove_global(state, "warn");
    remove_global(state, "require");

    /* Disable binary chunk export; luaL_loadbufferx below accepts text only. */
    lua_getglobal(state, LUA_STRLIBNAME);
    lua_pushnil(state);
    lua_setfield(state, -2, "dump");
    lua_pop(state, 1);

    *(ProbeBudget **)lua_getextraspace(state) = &budget;
    lua_sethook(state, instruction_hook, LUA_MASKCOUNT, 100);
    int code = luaL_loadbufferx(state, script, script_size, "tiarca-probe", "t");
    if (code == LUA_OK) {
        code = lua_pcall(state, 0, 0, 0);
    }
    lua_sethook(state, NULL, 0, 0);

    if (code == LUA_OK) {
        lua_getglobal(state, "result");
        const char *value = lua_tolstring(state, -1, NULL);
        snprintf(output, output_size, "%s", value == NULL ? "<no result>" : value);
        lua_pop(state, 1);
    } else {
        const char *message = lua_tostring(state, -1);
        snprintf(output, output_size, "%s", message == NULL ? "<Lua error>" : message);
    }
    lua_close(state);
    return code == LUA_OK ? 0 : code;
}

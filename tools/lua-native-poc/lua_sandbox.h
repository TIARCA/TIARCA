/* TIARCA Lua native proof-of-concept. NOT a production security boundary. */
#ifndef TIARCA_LUA_SANDBOX_H
#define TIARCA_LUA_SANDBOX_H

#include <stddef.h>

/*
 * Evaluates a text-only Lua chunk with a bounded allocator and instruction count.
 * The only observable result is the textual global 'result' or a textual error.
 * 0 = success, nonzero = Lua error. Never expose this API in the main IRC process.
 */
int tiarca_lua_poc_run(const char *script, size_t script_size,
                       size_t memory_budget_bytes, unsigned long instruction_budget,
                       char *output, size_t output_size);
#endif

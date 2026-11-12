#include "lua_sandbox.h"

#include <stdio.h>
#include <string.h>

#define MEM_BUDGET (2u * 1024u * 1024u)
#define INSTRUCTION_BUDGET 200000ul

static int assert_success(const char *script, const char *expected) {
    char output[256];
    int result = tiarca_lua_poc_run(script, strlen(script), MEM_BUDGET,
                                     INSTRUCTION_BUDGET, output, sizeof(output));
    if (result != 0 || strcmp(output, expected) != 0) {
        fprintf(stderr, "FAILED success expected '%s', got code=%d output='%s'\n",
                expected, result, output);
        return 1;
    }
    return 0;
}

static int assert_rejected(const char *script, const char *message_contains) {
    char output[256];
    int result = tiarca_lua_poc_run(script, strlen(script), MEM_BUDGET,
                                     INSTRUCTION_BUDGET, output, sizeof(output));
    if (result == 0 || strstr(output, message_contains) == NULL) {
        fprintf(stderr, "FAILED rejection expected '%s', got code=%d output='%s'\n",
                message_contains, result, output);
        return 1;
    }
    return 0;
}

int main(void) {
    int failed = 0;
    failed += assert_success("result = 6 * 7", "42");
    failed += assert_success("result = _VERSION", "Lua 5.5");
    failed += assert_success("result = string.upper('tiarca')", "TIARCA");
    failed += assert_success(
        "result = type(io)..':'..type(os)..':'..type(package)..':'..type(debug)"
        "..':'..type(require)..':'..type(dofile)..':'..type(loadfile)",
        "nil:nil:nil:nil:nil:nil:nil");
    failed += assert_rejected("return os.execute('echo unsafe')", "nil");
    failed += assert_rejected("while true do end", "instruction budget exceeded");
    failed += assert_rejected("result = string.rep('a', 32 * 1024 * 1024)", "memory");

    /* Reject non-text compiled Lua bytecode rather than evaluating it. */
    char output[256];
    const char bytecode[] = { 0x1b, 'L', 'u', 'a', 0, 1, 2 };
    if (tiarca_lua_poc_run(bytecode, sizeof(bytecode), MEM_BUDGET,
                           INSTRUCTION_BUDGET, output, sizeof(output)) == 0) {
        fprintf(stderr, "FAILED: bytecode accepted\n");
        failed += 1;
    }

    if (failed) {
        fprintf(stderr, "Lua native POC: %d failed check(s)\n", failed);
        return 1;
    }
    puts("Lua 5.5.1 native POC: 8 checks passed (HOST only).");
    return 0;
}

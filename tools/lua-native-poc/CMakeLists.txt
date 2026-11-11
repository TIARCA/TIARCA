cmake_minimum_required(VERSION 3.22)
project(tiarca_lua_native_poc LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(NOT DEFINED LUA_SOURCE_DIR OR NOT EXISTS "${LUA_SOURCE_DIR}/lua.h")
    message(FATAL_ERROR "Set -DLUA_SOURCE_DIR to the verified Lua 5.5.1 src/ directory")
endif()

# Lua 5.5.1 core plus only the libraries used by the allowlisted runtime.
# No interpreter executable, luac, OS, I/O, package loader or debug library.
set(LUA_FILES
    lapi.c lcode.c lctype.c ldebug.c ldo.c ldump.c lfunc.c lgc.c
    llex.c lmem.c lobject.c lopcodes.c lparser.c lstate.c lstring.c
    ltable.c ltm.c lundump.c lvm.c lzio.c
    lauxlib.c lbaselib.c lmathlib.c lstrlib.c ltablib.c lutf8lib.c
)
set(LUA_SOURCES)
foreach(filename IN LISTS LUA_FILES)
    if(NOT EXISTS "${LUA_SOURCE_DIR}/${filename}")
        message(FATAL_ERROR "Missing pinned Lua source: ${filename}")
    endif()
    list(APPEND LUA_SOURCES "${LUA_SOURCE_DIR}/${filename}")
endforeach()

add_library(tiarca_lua_engine STATIC ${LUA_SOURCES} lua_sandbox.c)
target_include_directories(tiarca_lua_engine PUBLIC "${LUA_SOURCE_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}")
set_target_properties(tiarca_lua_engine PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Normalize host source paths before comparing builds in two different directories.
if(CMAKE_C_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(tiarca_lua_engine PRIVATE
        "-ffile-prefix-map=${LUA_SOURCE_DIR}=lua"
        "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=poc")
endif()

if(ANDROID)
    # This is NOT added to the app APK. It is an isolated native build experiment.
    add_library(tiarca_lua_probe SHARED lua_probe_jni.c)
    target_link_libraries(tiarca_lua_probe PRIVATE tiarca_lua_engine m)
    target_link_options(tiarca_lua_probe PRIVATE
        "-Wl,--build-id=none"
        "-Wl,-z,max-page-size=16384")
else()
    enable_testing()
    add_executable(tiarca_lua_poc_tests lua_poc_tests.c)
    target_link_libraries(tiarca_lua_poc_tests PRIVATE tiarca_lua_engine m)
    add_test(NAME lua_55_restricted_host_test COMMAND tiarca_lua_poc_tests)
endif()

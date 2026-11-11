cmake_minimum_required(VERSION 3.22.1)
project(tiarca_lua LANGUAGES C)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
get_filename_component(LUA_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/lua-5.5.1" ABSOLUTE)
get_filename_component(LUA_PROBE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../tools/lua-native-poc" ABSOLUTE)
if(NOT EXISTS "${LUA_SOURCE_DIR}/lua.h")
    message(FATAL_ERROR "Missing vendored official Lua 5.5.1 sources")
endif()
# Core plus strictly allowlisted standard libraries; no io/os/package/debug library.
set(LUA_NATIVE_FILES
    lapi.c lcode.c lctype.c ldebug.c ldo.c ldump.c lfunc.c lgc.c
    llex.c lmem.c lobject.c lopcodes.c lparser.c lstate.c lstring.c
    ltable.c ltm.c lundump.c lvm.c lzio.c
    lauxlib.c lbaselib.c lmathlib.c lstrlib.c ltablib.c lutf8lib.c
)
set(LUA_NATIVE_SOURCES)
foreach(filename IN LISTS LUA_NATIVE_FILES)
    list(APPEND LUA_NATIVE_SOURCES "${LUA_SOURCE_DIR}/${filename}")
endforeach()
add_library(tiarca_lua_probe SHARED
    ${LUA_NATIVE_SOURCES}
    "${LUA_PROBE_DIR}/lua_sandbox.c"
    "${LUA_PROBE_DIR}/lua_probe_jni.c")
target_include_directories(tiarca_lua_probe PRIVATE "${LUA_SOURCE_DIR}" "${LUA_PROBE_DIR}")
target_link_libraries(tiarca_lua_probe PRIVATE m)
target_compile_options(tiarca_lua_probe PRIVATE
    "-ffile-prefix-map=${LUA_SOURCE_DIR}=lua"
    "-ffile-prefix-map=${LUA_PROBE_DIR}=lua-probe")
target_link_options(tiarca_lua_probe PRIVATE "-Wl,--build-id=none" "-Wl,-z,max-page-size=16384")

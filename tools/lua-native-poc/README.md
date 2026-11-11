# TIARCA - official Lua 5.5.1 native feasibility probe

Linked issue: https://github.com/TIARCA/TIARCA/issues/216

## Scope

This is deliberately NOT a TIARCA Lua implementation. The branch-only POC
proves that official Lua 5.5.1 C sources can be compiled into a native
library with an Android JNI entry point; it also runs host-only checks for
a restricted Lua environment. No Android component, Java class, manifest
entry, Gradle dependency or production APK is changed.

The experimental workflow runs only on its dedicated feature branch (or
manual dispatch). DO NOT MERGE THIS WORKFLOW AS-IS INTO MAIN. It downloads
the official source archive at workflow time; a production F-Droid-compatible
implementation must pin and provide its dependencies through the F-Droid build
process, preferably vendor audited source files, and build without hidden
unverified network downloads.

## Source and license

- Official source: https://www.lua.org/ftp/lua-5.5.1.tar.gz
- Version: 5.5.1 (official Lua, not LuaJ or LuaJava)
- Published SHA-256 (https://www.lua.org/download.html):
  1c4b4068d67061f2a2231ad2b5422e77acea1487ea9890f6320af614f4373dce
- Lua is MIT licensed. Preserve the license/copyright when vendoring.

CMake intentionally enumerates only core and allowlisted standard libraries.
It does not compile standalone lua/luac, package, os, io or debug libraries.

## Covered by this proof

- Lua arithmetic, string functions and Lua 5.5 version check;
- dangerous globals unavailable (io, os, package, debug, require, file loaders);
- infinite loop stopped via bounded instruction count;
- very large string allocation rejected via bounded allocator;
- precompiled bytecode rejected (Lua load mode t);
- Android NDK r28c ARM64 shared library compilation;
- byte-for-byte comparison of two independent Android CMake build directories;
- ELF LOAD segment alignment >= 16 KiB.

The last three tests require the branch GitHub Actions workflow to complete.

## Explicitly NOT verified

- Android emulator/device operation and isolated-process IPC behavior;
- no native code is added to TIARCA's app package in this experiment;
- APK delta, installed storage, PSS/RSS/USS, battery consumption;
- hostile-code audit, Android lifecycle, permissions, backup/restore;
- availability of the relevant native ABIs, production signing and F-Droid acceptance.

Allocator and instruction caps are defense in depth, not complete isolation.

## Host reproduction

Download the official tarball and verify the published SHA-256, then:

    cmake -S tools/lua-native-poc -B build/lua-host -DCMAKE_BUILD_TYPE=Release \
      -DLUA_SOURCE_DIR="$PWD/lua-5.5.1/src"
    cmake --build build/lua-host
    ctest --test-dir build/lua-host --output-on-failure

For Android reproduction see .github/workflows/lua-native-poc.yml.

No version bump, tag, GitHub Release, Candidate or updater changes.

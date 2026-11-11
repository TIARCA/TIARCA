package io.mrarm.irc.lua;

/** Experimental diagnostic JNI entry point, loaded by the isolated service only. */
final class NativeLuaProbe {
    static { System.loadLibrary("tiarca_lua_probe"); }
    private NativeLuaProbe() { }
    static native String runProbe(String source);
}

package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;

/**
 * JNI for persistent Lua states. Must only be called from the isolated service
 * HandlerThread. No Java Context or connection object reaches native code.
 */
final class NativeLuaRuntime {
    static {
        System.loadLibrary("tiarca_lua_probe");
    }

    private NativeLuaRuntime() { }

    static native byte[] load(String scriptId, byte[] source);
    static native byte[][] dispatch(String scriptId, String event, byte[] server,
                                    byte[] channel, byte[] nick, byte[] text,
                                    byte[] messageType, long timestamp,
                                    byte[][] channelNames, int[] memberCounts,
                                    boolean channelsTruncated, byte[][] userFields,
                                    boolean usersTruncated, boolean canReadUsers);
    static native void unload(String scriptId);
    static native void clear();

    static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    static String decode(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }
}

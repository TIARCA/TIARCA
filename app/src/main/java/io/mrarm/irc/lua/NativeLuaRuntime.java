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
    /** Bit positions follow native event_kind; -1 means unavailable. */
    static native int eventMask(String scriptId);
    /** Lua allocator only; not Java heap, native VM structs, IPC or total RSS. */
    static native long allocatedBytes();
    // Old signature retained for host regression tests and existing callers.
    static byte[][] dispatch(String scriptId, String event, byte[] server,
                             byte[] channel, byte[] nick, byte[] text,
                             byte[] messageType, long timestamp,
                             byte[][] channelNames, int[] memberCounts,
                             boolean channelsTruncated, byte[][] userFields,
                             boolean usersTruncated, boolean canReadUsers,
                             byte[][] storagePairs, boolean canUseStorage) {
        return dispatchWithIdentity(scriptId, event, server, channel, nick, text,
                messageType, timestamp, channelNames, memberCounts, channelsTruncated,
                userFields, usersTruncated, canReadUsers, storagePairs,
                canUseStorage, new byte[0][]);
    }

    /** Historical message sender data retrieved from SQLite, never inferred. */
    static byte[][] dispatchWithIdentity(String scriptId, String event, byte[] server,
                                                 byte[] channel, byte[] nick, byte[] text,
                                                 byte[] messageType, long timestamp,
                                                 byte[][] channelNames, int[] memberCounts,
                                                 boolean channelsTruncated, byte[][] userFields,
                                                 boolean usersTruncated, boolean canReadUsers,
                                                 byte[][] storagePairs, boolean canUseStorage,
                                                 byte[][] historyIdentity) {
        return dispatchVersioned(scriptId, event, server, channel, nick, text,
                messageType, timestamp, channelNames, memberCounts, channelsTruncated,
                userFields, usersTruncated, canReadUsers, storagePairs,
                canUseStorage, historyIdentity, 0);
    }

    /** Version zero forces a fresh snapshot (legacy host tests). */
    static byte[][] dispatchVersioned(String scriptId, String event, byte[] server,
            byte[] channel, byte[] nick, byte[] text, byte[] messageType, long timestamp,
            byte[][] channelNames, int[] memberCounts, boolean channelsTruncated,
            byte[][] userFields, boolean usersTruncated, boolean canReadUsers,
            byte[][] storagePairs, boolean canUseStorage, byte[][] historyIdentity,
            long storageVersion) {
        return dispatchWithMetadata(scriptId, event, server, channel, nick, text, messageType,
                timestamp, channelNames, memberCounts, channelsTruncated, userFields, usersTruncated,
                canReadUsers, storagePairs, canUseStorage, historyIdentity, storageVersion, new byte[0], new byte[0][]);
    }

    static native byte[][] dispatchWithMetadata(String scriptId, String event, byte[] server,
            byte[] channel, byte[] nick, byte[] text, byte[] messageType, long timestamp,
            byte[][] channelNames, int[] memberCounts, boolean channelsTruncated,
            byte[][] userFields, boolean usersTruncated, boolean canReadUsers,
            byte[][] storagePairs, boolean canUseStorage, byte[][] historyIdentity,
            long storageVersion, byte[] sourceId, byte[][] rosterRealnames);
    static native void unload(String scriptId);
    static native void clear();

    static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    static String decode(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }
}

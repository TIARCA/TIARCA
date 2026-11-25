package io.mrarm.irc.lua;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Worker-confined, read-only mirror of host-committed state, not speculative VM writes. */
final class LuaStorageMirror {
    private static final byte[][] EMPTY = new byte[0][];
    private static final class Revision {
        final long version;
        final byte[][] pairs;
        Revision(long version, byte[][] pairs) { this.version = version; this.pairs = pairs; }
    }
    private final Map<String, Revision> entries = new LinkedHashMap<String, Revision>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Revision> eldest) {
            return size() > LuaScriptStore.MAX_SCRIPTS;
        }
    };

    void clear() { entries.clear(); }

    byte[][] resolve(String script, long version, boolean allowed, List<String> pairs) {
        if (!allowed) {
            entries.remove(script);
            return pairs != null && pairs.isEmpty() ? EMPTY : null;
        }
        if (!LuaScriptStorage.validId(script) || version <= 0) return null;
        Revision current = entries.get(script);
        if (pairs == null)
            return current != null && current.version == version ? current.pairs : null;
        if (current != null && version < current.version) return null;
        if (pairs.size() > LuaScriptStorage.MAX_KEYS * 2 || pairs.size() % 2 != 0)
            return null;
        byte[][] encoded = new byte[pairs.size()][];
        Set<String> keys = new HashSet<>();
        int total = 0;
        for (int i = 0; i < pairs.size(); i += 2) {
            String key = pairs.get(i), value = pairs.get(i + 1);
            if (!LuaScriptStorage.validKey(key) || !keys.add(key) ||
                    !LuaScriptStorage.validValue(value)) return null;
            encoded[i] = key.getBytes(StandardCharsets.UTF_8);
            encoded[i + 1] = value.getBytes(StandardCharsets.UTF_8);
            total += encoded[i].length + encoded[i + 1].length;
            if (total > LuaScriptStorage.MAX_TOTAL_BYTES) return null;
        }
        // Same revision must never acquire different contents.
        if (current != null && version == current.version &&
                !java.util.Arrays.deepEquals(current.pairs, encoded)) return null;
        entries.put(script, new Revision(version, encoded));
        return encoded;
    }
}

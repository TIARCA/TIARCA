package io.mrarm.irc.lua;

/**
 * The read-only cross-channel WHO gate accepts only the exact channel and
 * pattern explicitly typed by the local user. A Lua script cannot turn an
 * unrelated event or another /lua command into a different WHO target.
 */
final class MaskFinderCommandPolicy {
    private MaskFinderCommandPolicy() { }

    static boolean permits(LuaScriptStore.Script script, String server,
                           String event, String sourceChannel, String commandText,
                           String targetChannel, String pattern) {
        if (!"command".equals(event) ||
                !MaskFinderManager.approvedForNetwork(script, server) ||
                !MaskFinderMatcher.validQuery(pattern) ||
                !LuaModerationPolicy.validAtom(targetChannel, 64, 96) ||
                "#&+!".indexOf(targetChannel.charAt(0)) < 0 ||
                commandText == null) return false;

        String[] words = commandText.trim().split("\\s+");
        if (words.length < 2 || words.length > 3 ||
                !"mask".equalsIgnoreCase(words[0])) return false;
        if (words.length == 2)
            // Backward-compatible /lua mask <pattern>, only from the same channel.
            return targetChannel.equals(sourceChannel) && pattern.equals(words[1]);

        // Explicit /lua mask <#channel> <pattern>, from any tab of this server.
        // A subsequent live joined-channel check is mandatory in MaskFinderManager.
        return targetChannel.equals(words[1]) && pattern.equals(words[2]);
    }
}

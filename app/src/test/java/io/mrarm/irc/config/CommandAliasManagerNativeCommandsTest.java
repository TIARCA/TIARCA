package io.mrarm.irc.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class CommandAliasManagerNativeCommandsTest {
    private static final Map<String, String> EXPECTED = new HashMap<>();
    private static final Map<String, String[]> VALID = new HashMap<>();
    static {
        EXPECTED.put("whowas", "WHOWAS");
        EXPECTED.put("accept", "ACCEPT");
        EXPECTED.put("invite", "INVITE");
        EXPECTED.put("ison", "ISON");
        EXPECTED.put("userhost", "USERHOST");
        EXPECTED.put("motd", "MOTD");
        EXPECTED.put("version", "VERSION");
        EXPECTED.put("time", "TIME");
        EXPECTED.put("admin", "ADMIN");
        EXPECTED.put("info", "INFO");
        EXPECTED.put("lusers", "LUSERS");
        EXPECTED.put("links", "LINKS");
        EXPECTED.put("stats", "STATS");
        EXPECTED.put("knock", "KNOCK");
        EXPECTED.put("list", "LIST");
        EXPECTED.put("names", "NAMES");
        EXPECTED.put("who", "WHO");

        VALID.put("whowas", new String[] { "whowas", "nick" });
        VALID.put("accept", new String[] { "accept", "+nick" });
        VALID.put("invite", new String[] { "invite", "nick", "#channel" });
        VALID.put("ison", new String[] { "ison", "nick", "other" });
        VALID.put("userhost", new String[] { "userhost", "nick" });
        VALID.put("motd", new String[] { "motd" });
        VALID.put("version", new String[] { "version" });
        VALID.put("time", new String[] { "time" });
        VALID.put("admin", new String[] { "admin" });
        VALID.put("info", new String[] { "info" });
        VALID.put("lusers", new String[] { "lusers" });
        VALID.put("links", new String[] { "links" });
        VALID.put("stats", new String[] { "stats", "u" });
        VALID.put("knock", new String[] { "knock", "#channel", "hello" });
        VALID.put("list", new String[] { "list" });
        VALID.put("names", new String[] { "names" });
        VALID.put("who", new String[] { "who" });
    }

    @Test
    public void nativeCommandsAreBuiltInRawAliasesAndParseValidExamples() {
        Map<String, CommandAliasManager.CommandAlias> aliases = new HashMap<>();
        for (CommandAliasManager.CommandAlias alias : CommandAliasManager.getDefaultAliases())
            aliases.put(alias.name, alias);

        for (Map.Entry<String, String> expected : EXPECTED.entrySet()) {
            CommandAliasManager.CommandAlias alias = aliases.get(expected.getKey());
            assertNotNull("Missing /" + expected.getKey(), alias);
            assertEquals(CommandAliasManager.CommandAlias.MODE_RAW, alias.mode);
            assertEquals(expected.getValue() + " ${args}", alias.text);
            assertNotNull("Invalid syntax parser for /" + expected.getKey(), alias.getSyntaxParser());
            assertTrue("Valid example rejected for /" + expected.getKey(),
                    alias.checkSyntaxMatches(null, VALID.get(expected.getKey())));
        }
    }

    @Test
    public void acceptRequiresExplicitPlusOrMinusPrefix() {
        CommandAliasManager.CommandAlias accept = null;
        for (CommandAliasManager.CommandAlias alias : CommandAliasManager.getDefaultAliases()) {
            if ("accept".equals(alias.name)) {
                accept = alias;
                break;
            }
        }
        assertNotNull(accept);
        assertTrue(accept.checkSyntaxMatches(null, new String[] { "accept", "+nick" }));
        assertTrue(accept.checkSyntaxMatches(null, new String[] { "accept", "-nick" }));
        assertTrue(accept.checkSyntaxMatches(null,
                new String[] { "accept", "+nick", "-other" }));
        assertFalse(accept.checkSyntaxMatches(null, new String[] { "accept", "nick" }));
        assertFalse(accept.checkSyntaxMatches(null, new String[] { "accept" }));
    }
}

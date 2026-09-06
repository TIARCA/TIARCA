package io.mrarm.irc.config;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommandAliasManagerTest {

    private CommandAliasManager manager;

    @Before
    public void setUp() {
        manager = new CommandAliasManager(null);
    }

    @Test
    public void saveAndLoadUserSettings_preservesCommandAliasTypeAndFields() {
        List<CommandAliasManager.CommandAlias> aliases = manager.getUserAliases();
        aliases.clear();

        CommandAliasManager.CommandAlias custom = new CommandAliasManager.CommandAlias();
        custom.name = "myalias";
        custom.syntax = "<target> <msg...>";
        custom.text = "PRIVMSG ${target} :${msg}";
        custom.mode = CommandAliasManager.CommandAlias.MODE_MESSAGE;
        custom.channel = "${target}";
        aliases.add(custom);

        StringWriter writer = new StringWriter();
        manager.saveUserSettings(writer);

        String json = writer.toString();
        assertTrue(json.contains("myalias"));

        manager.loadUserSettings(new StringReader(json));

        List<CommandAliasManager.CommandAlias> loaded = manager.getUserAliases();
        assertEquals(1, loaded.size());
        Object item = loaded.get(0);
        assertTrue("Loaded item must be a CommandAlias, not " + item.getClass().getName(),
                item instanceof CommandAliasManager.CommandAlias);

        CommandAliasManager.CommandAlias loadedAlias = (CommandAliasManager.CommandAlias) item;
        assertEquals("myalias", loadedAlias.name);
        assertEquals("<target> <msg...>", loadedAlias.syntax);
        assertEquals("PRIVMSG ${target} :${msg}", loadedAlias.text);
        assertEquals(CommandAliasManager.CommandAlias.MODE_MESSAGE, loadedAlias.mode);
        assertEquals("${target}", loadedAlias.channel);
    }

    @Test
    public void loadLegacyArrayFormat_deserializesCorrectly() {
        String jsonArray = "["
                + "{\"name\":\"legacycmd\",\"syntax\":\"<arg>\",\"text\":\"RAW ${arg}\",\"mode\":2}"
                + "]";

        manager.loadUserSettings(new StringReader(jsonArray));

        List<CommandAliasManager.CommandAlias> loaded = manager.getUserAliases();
        assertEquals(1, loaded.size());
        Object item = loaded.get(0);
        assertTrue("Legacy item must be CommandAlias", item instanceof CommandAliasManager.CommandAlias);

        CommandAliasManager.CommandAlias alias = (CommandAliasManager.CommandAlias) item;
        assertEquals("legacycmd", alias.name);
        assertEquals("<arg>", alias.syntax);
        assertEquals("RAW ${arg}", alias.text);
        assertEquals(CommandAliasManager.CommandAlias.MODE_RAW, alias.mode);
    }

    @Test
    public void loadLinkedTreeMapAndJsonObject_recoversToCommandAlias() {
        // Simulate untyped list (e.g., produced by raw Gson deserialization without type info)
        Map<String, Object> mapItem = new HashMap<>();
        mapItem.put("name", "mapcmd");
        mapItem.put("syntax", "<text>");
        mapItem.put("text", "NOTICE #test :${text}");
        mapItem.put("mode", 0);

        List<Object> rawList = new ArrayList<>();
        rawList.add(mapItem);

        List<CommandAliasManager.CommandAlias> sanitized = manager.sanitizeUserAliases(rawList);
        assertEquals(1, sanitized.size());
        Object item = sanitized.get(0);
        assertTrue("Sanitized item must be CommandAlias", item instanceof CommandAliasManager.CommandAlias);

        CommandAliasManager.CommandAlias alias = (CommandAliasManager.CommandAlias) item;
        assertEquals("mapcmd", alias.name);
        assertEquals("<text>", alias.syntax);
        assertEquals("NOTICE #test :${text}", alias.text);
        assertEquals(0, alias.mode);
    }

    @Test
    public void autocompleteIteration_worksWithoutClassCastException() {
        CommandAliasManager.CommandAlias custom = CommandAliasManager.CommandAlias.raw("testcmd", "<arg>", "RAW ${arg}");
        manager.getUserAliases().add(custom);

        StringWriter writer = new StringWriter();
        manager.saveUserSettings(writer);
        manager.loadUserSettings(new StringReader(writer.toString()));

        int count = 0;
        for (CommandAliasManager.CommandAlias alias : manager.getUserAliases()) {
            assertNotNull(alias.name);
            count++;
        }
        assertEquals(1, count);
    }

}

package io.mrarm.irc.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.dto.ChannelModeMessageInfo;
import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MessageBuilderModeTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private ChannelModeMessageInfo.Entry nickEntry(char mode, String nick, boolean isRemoved) {
        return new ChannelModeMessageInfo.Entry(ChannelModeMessageInfo.EntryType.NICK_FLAG, mode, nick, isRemoved);
    }

    private MessageInfo createModeMessage(String senderNick, List<ChannelModeMessageInfo.Entry> entries) {
        MessageSenderInfo sender = senderNick != null ? new MessageSenderInfo(senderNick, null, null, null, null) : null;
        return new ChannelModeMessageInfo(sender, new Date(0), entries);
    }

    private void assertEndsWith(String expectedSuffix, CharSequence actual) {
        assertTrue("Expected string ending with: \"" + expectedSuffix + "\", but got: \"" + actual + "\"",
                actual.toString().endsWith(expectedSuffix));
    }

    @Test
    public void testItalianNickModesGaveAndRemoved() {
        setLocale(Locale.ITALIAN);
        MessageBuilder builder = new MessageBuilder(context);

        // +v: "BOtAmIcIziA ha dato il voice a mimancaunvenerdi"
        List<ChannelModeMessageInfo.Entry> entries = new ArrayList<>();
        entries.add(nickEntry('v', "mimancaunvenerdi", false));
        MessageInfo msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA ha dato il voice a mimancaunvenerdi", builder.buildMessage(msg));

        // -v: "BOtAmIcIziA ha tolto il voice a mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('v', "mimancaunvenerdi", true));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA ha tolto il voice a mimancaunvenerdi", builder.buildMessage(msg));

        // +o: "BOtAmIcIziA ha dato lo stato di operatore del canale a mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('o', "mimancaunvenerdi", false));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA ha dato lo stato di operatore del canale a mimancaunvenerdi", builder.buildMessage(msg));

        // -o: "BOtAmIcIziA ha tolto lo stato di operatore del canale a mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('o', "mimancaunvenerdi", true));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA ha tolto lo stato di operatore del canale a mimancaunvenerdi", builder.buildMessage(msg));

        // +h: "X ha dato lo stato di half-op del canale a Y"
        entries.clear();
        entries.add(nickEntry('h', "Y", false));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha dato lo stato di half-op del canale a Y", builder.buildMessage(msg));

        // -h: "X ha tolto lo stato di half-op del canale a Y"
        entries.clear();
        entries.add(nickEntry('h', "Y", true));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha tolto lo stato di half-op del canale a Y", builder.buildMessage(msg));

        // +a: "X ha dato lo stato di amministratore del canale a Y"
        entries.clear();
        entries.add(nickEntry('a', "Y", false));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha dato lo stato di amministratore del canale a Y", builder.buildMessage(msg));

        // -a: "X ha tolto lo stato di amministratore del canale a Y"
        entries.clear();
        entries.add(nickEntry('a', "Y", true));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha tolto lo stato di amministratore del canale a Y", builder.buildMessage(msg));

        // +q: "X ha dato lo stato di proprietario del canale a Y"
        entries.clear();
        entries.add(nickEntry('q', "Y", false));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha dato lo stato di proprietario del canale a Y", builder.buildMessage(msg));

        // -q: "X ha tolto lo stato di proprietario del canale a Y"
        entries.clear();
        entries.add(nickEntry('q', "Y", true));
        msg = createModeMessage("X", entries);
        assertEndsWith("X ha tolto lo stato di proprietario del canale a Y", builder.buildMessage(msg));
    }

    @Test
    public void testEnglishNickModesGaveAndRemoved() {
        setLocale(Locale.ENGLISH);
        MessageBuilder builder = new MessageBuilder(context);

        // +v: "BOtAmIcIziA gave voice to mimancaunvenerdi"
        List<ChannelModeMessageInfo.Entry> entries = new ArrayList<>();
        entries.add(nickEntry('v', "mimancaunvenerdi", false));
        MessageInfo msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA gave voice to mimancaunvenerdi", builder.buildMessage(msg));

        // -v: "BOtAmIcIziA removed voice from mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('v', "mimancaunvenerdi", true));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA removed voice from mimancaunvenerdi", builder.buildMessage(msg));

        // +o: "BOtAmIcIziA gave channel operator status to mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('o', "mimancaunvenerdi", false));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA gave channel operator status to mimancaunvenerdi", builder.buildMessage(msg));

        // -o: "BOtAmIcIziA removed channel operator status from mimancaunvenerdi"
        entries.clear();
        entries.add(nickEntry('o', "mimancaunvenerdi", true));
        msg = createModeMessage("BOtAmIcIziA", entries);
        assertEndsWith("BOtAmIcIziA removed channel operator status from mimancaunvenerdi", builder.buildMessage(msg));
    }

    @Test
    public void testMultipleModesAndNicks() {
        setLocale(Locale.ITALIAN);
        MessageBuilder builder = new MessageBuilder(context);

        // Multiple modes (+vo) on same nick
        List<ChannelModeMessageInfo.Entry> entries = new ArrayList<>();
        entries.add(nickEntry('v', "UserA", false));
        entries.add(nickEntry('o', "UserA", false));
        MessageInfo msg = createModeMessage("ServerOp", entries);
        CharSequence result = builder.buildMessage(msg);
        String text = result.toString();
        assertTrue(text.contains("ServerOp ha dato "));
        assertTrue(text.contains(" a UserA"));
        assertTrue(text.contains("il voice"));
        assertTrue(text.contains("lo stato di operatore del canale"));

        // Multiple nicks
        entries.clear();
        entries.add(nickEntry('v', "UserA", false));
        entries.add(nickEntry('v', "UserB", false));
        msg = createModeMessage("ServerOp", entries);
        result = builder.buildMessage(msg);
        text = result.toString();
        assertTrue(text.contains("ServerOp ha dato "));
        assertTrue(text.contains("il voice a UserA"));
        assertTrue(text.contains("il voice a UserB"));
    }

    @Test
    public void testResourcePlaceholdersValidation() throws Exception {
        java.io.File resDir = new java.io.File("../app/src/main/res");
        if (!resDir.exists()) {
            resDir = new java.io.File("src/main/res");
        }
        assertTrue("Resource directory not found", resDir.exists());

        java.io.File valuesDir = new java.io.File(resDir, "values");
        java.util.Map<String, ResourceInfo> baseResources = loadResourcesFromDirectory(valuesDir);

        java.io.File[] valueDirs = resDir.listFiles((dir, name) -> name.startsWith("values"));
        assertTrue("No values directories found", valueDirs != null && valueDirs.length > 0);

        for (java.io.File valDir : valueDirs) {
            java.util.Map<String, ResourceInfo> localeResources = loadResourcesFromDirectory(valDir);
            for (java.util.Map.Entry<String, ResourceInfo> entry : localeResources.entrySet()) {
                String key = entry.getKey();
                ResourceInfo resInfo = entry.getValue();

                // Rule 1: Max positional index cannot exceed the total number of placeholders in this specific string
                if (resInfo.maxPositionalIndex != -1) {
                    assertTrue("Invalid positional placeholder index %" + resInfo.maxPositionalIndex
                                    + "$ (exceeds total placeholder count " + resInfo.totalPlaceholderCount
                                    + ") in " + resInfo.sourceFile + " for element " + key + ": \"" + resInfo.rawText + "\"",
                            resInfo.maxPositionalIndex <= resInfo.totalPlaceholderCount);
                }

                // Rule 2: Compare against base English resource if present
                ResourceInfo baseInfo = baseResources.get(key);
                if (baseInfo != null) {
                    int expectedCount = baseInfo.totalPlaceholderCount;
                    if (resInfo.maxPositionalIndex != -1) {
                        assertTrue("Positional placeholder index %" + resInfo.maxPositionalIndex
                                        + "$ exceeds expected base argument count (" + expectedCount
                                        + ") in " + resInfo.sourceFile + " for element " + key + ": \"" + resInfo.rawText + "\"",
                                resInfo.maxPositionalIndex <= expectedCount);
                    }
                    assertTrue("Placeholder count (" + resInfo.totalPlaceholderCount
                                    + ") exceeds expected base argument count (" + expectedCount
                                    + ") in " + resInfo.sourceFile + " for element " + key + ": \"" + resInfo.rawText + "\"",
                            resInfo.totalPlaceholderCount <= expectedCount);
                }
            }
        }
    }

    private java.util.Map<String, ResourceInfo> loadResourcesFromDirectory(java.io.File dir) throws Exception {
        java.util.Map<String, ResourceInfo> map = new java.util.HashMap<>();
        java.io.File[] xmlFiles = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (xmlFiles == null) return map;

        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        java.util.regex.Pattern placeholderPattern = java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z])");

        for (java.io.File xmlFile : xmlFiles) {
            org.w3c.dom.Document doc = db.parse(xmlFile);

            org.w3c.dom.NodeList stringNodes = doc.getElementsByTagName("string");
            for (int i = 0; i < stringNodes.getLength(); i++) {
                org.w3c.dom.Element elem = (org.w3c.dom.Element) stringNodes.item(i);
                String name = elem.getAttribute("name");
                String text = elem.getTextContent();
                if (name != null && !name.isEmpty() && text != null) {
                    map.put(name, parseResourceInfo(xmlFile.getPath(), text, placeholderPattern));
                }
            }

            org.w3c.dom.NodeList pluralNodes = doc.getElementsByTagName("plurals");
            for (int i = 0; i < pluralNodes.getLength(); i++) {
                org.w3c.dom.Element pluralElem = (org.w3c.dom.Element) pluralNodes.item(i);
                String pluralName = pluralElem.getAttribute("name");
                org.w3c.dom.NodeList itemNodes = pluralElem.getElementsByTagName("item");
                for (int j = 0; j < itemNodes.getLength(); j++) {
                    org.w3c.dom.Element itemElem = (org.w3c.dom.Element) itemNodes.item(j);
                    String quantity = itemElem.getAttribute("quantity");
                    String text = itemElem.getTextContent();
                    if (pluralName != null && quantity != null && text != null) {
                        map.put(pluralName + "[" + quantity + "]", parseResourceInfo(xmlFile.getPath(), text, placeholderPattern));
                    }
                }
            }
        }
        return map;
    }

    private ResourceInfo parseResourceInfo(String sourceFile, String text, java.util.regex.Pattern pattern) {
        ResourceInfo info = new ResourceInfo();
        info.sourceFile = sourceFile;
        info.rawText = text;
        String cleanText = text.replace("%%", "");
        java.util.regex.Matcher matcher = pattern.matcher(cleanText);
        while (matcher.find()) {
            info.totalPlaceholderCount++;
            String posStr = matcher.group(1);
            if (posStr != null) {
                int pos = Integer.parseInt(posStr);
                if (pos > info.maxPositionalIndex) {
                    info.maxPositionalIndex = pos;
                }
            }
            info.placeholderTypes.add(matcher.group(2));
        }
        return info;
    }

    private static class ResourceInfo {
        String sourceFile;
        String rawText;
        int totalPlaceholderCount = 0;
        int maxPositionalIndex = -1;
        List<String> placeholderTypes = new ArrayList<>();
    }
}

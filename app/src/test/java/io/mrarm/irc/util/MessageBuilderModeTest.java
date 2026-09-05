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
}

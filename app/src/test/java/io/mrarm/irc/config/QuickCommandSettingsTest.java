package io.mrarm.irc.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuickCommandSettingsTest {

    @Test
    public void normalizesActivationWords() {
        assertEquals("!film", QuickCommandSettings.normalizeTrigger(" film ", "!movie"));
        assertEquals("!wiki2", QuickCommandSettings.normalizeTrigger("!WIKI2", "!wiki"));
    }

    @Test
    public void rejectsAmbiguousActivationWords() {
        assertEquals("!movie", QuickCommandSettings.normalizeTrigger("!film italiano", "!movie"));
        assertEquals("!calc", QuickCommandSettings.normalizeTrigger("!", "!calc"));
    }
}

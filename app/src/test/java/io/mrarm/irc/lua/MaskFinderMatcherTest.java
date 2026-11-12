package io.mrarm.irc.lua;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaskFinderMatcherTest {
    @Test public void shortSearchMatchesAnywhereAcrossEntireMask() {
        assertTrue(MaskFinderMatcher.matches("pippo", "pippo!user@host.net"));
        assertTrue(MaskFinderMatcher.matches("pippo", "appippo!user@host.net"));
        assertTrue(MaskFinderMatcher.matches("pippo", "Nick!pippolone@host.net"));
        assertTrue(MaskFinderMatcher.matches("pippo", "Nick!user@pippo.543.net"));
        assertTrue(MaskFinderMatcher.matches("PiPpO", "nick!user@PIPPO.net"));
        assertFalse(MaskFinderMatcher.matches("pippo", "nick!user@host.net"));
    }

    @Test public void ircWildcardsHaveStandardZeroOrMoreAndOneSemantics() {
        assertTrue(MaskFinderMatcher.matches("*pippo*", "pippo"));
        assertTrue(MaskFinderMatcher.matches("n?ck!*@*.net",
                "nick!abc@sub.example.net"));
        assertFalse(MaskFinderMatcher.matches("n?ck!*@*.net",
                "nck!abc@sub.example.net"));
        assertTrue(MaskFinderMatcher.matches("pip*", "xxpippolone!id@host"));
        assertFalse(MaskFinderMatcher.matches("*!*@*.net",
                "nick!id@sub.example.org"));
    }

    @Test public void invalidControlStringsDoNotMatch() {
        assertFalse(MaskFinderMatcher.matches("a\nb", "a\nb!id@host"));
        assertFalse(MaskFinderMatcher.matches("a\\b", "a\\b!id@host"));
        assertFalse(MaskFinderMatcher.matches("", "nick!ident@host"));
    }
}

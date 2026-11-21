package io.mrarm.irc.config;

import org.junit.Test;

import java.util.regex.Pattern;

import io.mrarm.irc.NotificationManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NotificationRuleNickCacheTest {

    private static NotificationRule nickRule() {
        return new NotificationRule("nick", NotificationRule.AppliesToEntry.channelEvents(),
                "(^|[ ,:;@])${nick}($|[ ,:;'?])", true);
    }

    @Test
    public void recognizesAutocompletedTabAndRebuildsAfterNickChange() {
        NotificationRule rule = nickRule();
        NotificationManager.ConnectionManager conn = new NotificationManager.ConnectionManager(null);

        Pattern initial = rule.getCompiledPattern(conn, "Marco");
        assertTrue(initial.matcher("Marco: puoi leggere?").find());
        assertFalse(initial.matcher("MarcoAway: puoi leggere?").find());
        assertSame(initial, rule.getCompiledPattern(conn, "Marco"));

        Pattern renamed = rule.getCompiledPattern(conn, "MarcoAway");
        assertTrue(renamed.matcher("MarcoAway: puoi leggere?").find());
        assertFalse(renamed.matcher("Marco: puoi leggere?").find());
        assertSame(renamed, rule.getCompiledPattern(conn, "MarcoAway"));
    }

    @Test
    public void sameRuleUsesTheCurrentNickForEachConnection() {
        NotificationRule rule = nickRule();
        NotificationManager.ConnectionManager serverA = new NotificationManager.ConnectionManager(null);
        NotificationManager.ConnectionManager serverB = new NotificationManager.ConnectionManager(null);

        Pattern alice = rule.getCompiledPattern(serverA, "Alice");
        Pattern bob = rule.getCompiledPattern(serverB, "Bob");
        assertTrue(alice.matcher("Alice, ci sei?").find());
        assertTrue(bob.matcher("Bob: ciao").find());
        assertFalse(bob.matcher("Alice: ciao").find());
        assertSame(alice, rule.getCompiledPattern(serverA, "Alice"));
    }

    @Test
    public void editingDynamicRuleAlsoRefreshesCachedPattern() {
        NotificationRule rule = nickRule();
        NotificationManager.ConnectionManager conn = new NotificationManager.ConnectionManager(null);
        assertTrue(rule.getCompiledPattern(conn, "Marco").matcher("Marco: prova").find());

        rule.setRegex("(^|[ ])${nick}($|[.!])", true);
        assertTrue(rule.getCompiledPattern(conn, "Marco").matcher("ciao Marco!").find());
        assertFalse(rule.getCompiledPattern(conn, "Marco").matcher("Marco: prova").find());

        rule.setRegex("test", false);
        assertTrue(rule.getCompiledPattern(conn, "Marco").matcher("test").find());
        rule.setRegex("altro", false);
        assertFalse(rule.getCompiledPattern(conn, "Marco").matcher("test").find());
        assertTrue(rule.getCompiledPattern(conn, "Marco").matcher("altro").find());
    }

    @Test
    public void quotedNicknameWithRegexCharactersMatchesLiterally() {
        NotificationRule rule = nickRule();
        NotificationManager.ConnectionManager conn = new NotificationManager.ConnectionManager(null);
        Pattern pattern = rule.getCompiledPattern(conn, "Marco[IT]");
        assertTrue(pattern.matcher("Marco[IT]: prova").find());
        assertFalse(pattern.matcher("MarcoI: prova").find());
    }
}

package io.mrarm.irc.irc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SimosnapVerificationManagerTest {

    @Test
    public void recognizesSimosnapServerHostsOnly() {
        assertTrue(SimosnapVerificationManager.isSimosnapServerName(
                "resurrection.simosnap.com"));
        assertTrue(SimosnapVerificationManager.isSimosnapServerName(
                "irc.simosnap.org"));
        assertFalse(SimosnapVerificationManager.isSimosnapServerName(
                "simosnap.example.org"));
        assertFalse(SimosnapVerificationManager.isSimosnapServerName(null));
    }

    @Test
    public void extractsAnswerFromQuoteSolveInstruction() {
        SimosnapVerificationManager.Challenge challenge =
                SimosnapVerificationManager.parseChallenge(
                        "Per continuare digita /quote solve 8.");

        assertNotNull(challenge);
        assertEquals("8", challenge.suggestedAnswer);
    }

    @Test
    public void acceptsRawQuoteAndDirectSolveVariants() {
        SimosnapVerificationManager.Challenge rawQuote =
                SimosnapVerificationManager.parseChallenge(
                        "Use /raw quote solve 42 to continue");
        SimosnapVerificationManager.Challenge direct =
                SimosnapVerificationManager.parseChallenge(
                        "Use /solve 17 to continue");

        assertNotNull(rawQuote);
        assertEquals("42", rawQuote.suggestedAnswer);
        assertNotNull(direct);
        assertEquals("17", direct.suggestedAnswer);
    }

    @Test
    public void keepsChallengeButDoesNotPrefillPlaceholder() {
        SimosnapVerificationManager.Challenge challenge =
                SimosnapVerificationManager.parseChallenge(
                        "Answer the question, then type /quote solve <answer>");

        assertNotNull(challenge);
        assertNull(challenge.suggestedAnswer);
    }

    @Test
    public void ignoresOrdinarySolveText() {
        assertNull(SimosnapVerificationManager.parseChallenge(
                "Please solve the question shown above."));
    }

    @Test
    public void ignoresIrcFormattingAroundInstruction() {
        SimosnapVerificationManager.Challenge challenge =
                SimosnapVerificationManager.parseChallenge(
                        "\u0002Verify:\u000f /quote \u0002solve\u000f 9");

        assertNotNull(challenge);
        assertEquals("9", challenge.suggestedAnswer);
    }
}

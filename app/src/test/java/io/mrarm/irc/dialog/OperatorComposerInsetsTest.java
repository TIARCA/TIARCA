package io.mrarm.irc.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Source-level guard; keyboard placement still requires Candidate device checks. */
public class OperatorComposerInsetsTest {
    @Test public void dialogUsesImeAndSystemBarInsetsInsteadOfDeprecatedResize() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/mrarm/irc/dialog/OperatorActionsComposerDialog.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"));
        assertTrue(source.contains("ViewCompat.setOnApplyWindowInsetsListener(root,"));
        assertTrue(source.contains("WindowInsetsCompat.Type.ime()"));
        assertTrue(source.contains("WindowInsetsCompat.Type.systemBars()"));
        assertTrue(source.contains("WindowInsetsCompat.Type.displayCutout()"));
        assertTrue(source.contains("scroll.addOnLayoutChangeListener("));
        assertFalse(source.contains("SOFT_INPUT_ADJUST_RESIZE"));
    }
}

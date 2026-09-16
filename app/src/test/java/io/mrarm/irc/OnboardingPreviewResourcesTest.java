package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression coverage for the runtime-rendered onboarding preset preview. */
public class OnboardingPreviewResourcesTest {

    @Test
    public void onboardingUsesRuntimeChatPreviewWithoutDedicatedScreenshots() throws IOException {
        String layout = read("src/main/res/layout/activity_onboarding.xml");
        String previewView = read("src/main/java/io/mrarm/irc/view/OnboardingPresetPreviewView.java");

        assertTrue(layout.contains("io.mrarm.irc.view.OnboardingPresetPreviewView"));
        assertTrue(previewView.contains("R.layout.chat_message"));
        assertTrue(previewView.contains("new MessageBuilder(getContext())"));
        assertTrue(previewView.contains("new NickPrefixList(prefix)"));
        assertTrue(previewView.contains("Lætitia!"));
        assertTrue(previewView.contains("Frederick! At last! 😄"));
        assertTrue(previewView.contains("aunt Augusta"));
        assertTrue(previewView.contains("22, 48, 41"));

        String[] obsoleteScreenshots = {
                "onboarding_preset_graphic_light",
                "onboarding_preset_graphic_dark",
                "onboarding_preset_irc_light",
                "onboarding_preset_irc_dark",
                "onboarding_preset_terminal",
                "onboarding_preset_color_blind"
        };
        for (String name : obsoleteScreenshots) {
            Path image = path("src/main/res/drawable-nodpi/" + name + ".webp");
            assertFalse("Obsolete dedicated onboarding preview still present: " + name,
                    Files.exists(image));
            assertFalse("Runtime preview still references obsolete screenshot: " + name,
                    previewView.contains("R.drawable." + name));
        }

        // OnboardingActivity still decodes the old compact sprite before calling setImageBitmap().
        // The custom preview view ignores that bitmap; keeping this tiny trigger avoids widening
        // the first experiment into an unrelated onboarding-activity refactor.
        assertTrue(Files.exists(path("src/main/res/drawable-nodpi/onboarding_preset_previews.webp")));
    }

    private static Path path(String relative) {
        Path path = Paths.get(relative);
        if (!Files.exists(path))
            path = Paths.get("app").resolve(relative);
        return path;
    }

    private static String read(String relative) throws IOException {
        return new String(Files.readAllBytes(path(relative)), StandardCharsets.UTF_8);
    }
}

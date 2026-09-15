package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression coverage for the per-preset onboarding preview resources. */
public class OnboardingPreviewResourcesTest {

    @Test
    public void onboardingUsesDedicatedPreviewViewAndAllSixResources() throws IOException {
        String layout = read("src/main/res/layout/activity_onboarding.xml");
        String previewView = read("src/main/java/io/mrarm/irc/view/OnboardingPresetPreviewView.java");

        assertTrue(layout.contains("io.mrarm.irc.view.OnboardingPresetPreviewView"));

        String[] names = {
                "onboarding_preset_graphic_light",
                "onboarding_preset_graphic_dark",
                "onboarding_preset_irc_light",
                "onboarding_preset_irc_dark",
                "onboarding_preset_terminal",
                "onboarding_preset_color_blind"
        };
        for (String name : names) {
            Path image = path("src/main/res/drawable-nodpi/" + name + ".webp");
            assertTrue("Missing dedicated onboarding preview: " + name, Files.exists(image));
            assertTrue("Onboarding preview is unexpectedly empty: " + name,
                    Files.size(image) > 2000);
            assertTrue("Preview view does not map resource: " + name,
                    previewView.contains("R.drawable." + name));
        }
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

package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Structural regression coverage for the first-run wizard and its safe relaunch path. */
public class OnboardingStructureTest {

    @Test
    public void launcherRoutesThroughEntryActivity() throws IOException {
        String manifest = read("src/main/AndroidManifest.xml");
        int entry = manifest.indexOf(".onboarding.EntryActivity");
        int launcher = manifest.indexOf("android.intent.category.LAUNCHER", entry);
        int main = manifest.indexOf(".MainActivity");
        assertTrue(entry >= 0 && launcher > entry);
        assertTrue(main > entry);
        String mainBlock = manifest.substring(main,
                manifest.indexOf("</activity>", main));
        assertFalse("MainActivity must not retain the launcher category",
                mainBlock.contains("android.intent.category.LAUNCHER"));
    }

    @Test
    public void wizardShowsAllBuiltInStylesAndImport() throws IOException {
        String xml = read("src/main/res/layout/activity_onboarding.xml");
        assertTrue(xml.contains("onboarding_preset_graphic_light"));
        assertTrue(xml.contains("onboarding_preset_graphic_dark"));
        assertTrue(xml.contains("onboarding_preset_irc_light"));
        assertTrue(xml.contains("onboarding_preset_irc_dark"));
        assertTrue(xml.contains("onboarding_preset_terminal"));
        assertTrue(xml.contains("onboarding_preset_color_blind"));
        assertTrue(xml.contains("onboarding_import_preset"));
    }

    @Test
    public void identityAndNetworkAdvancedFieldsRemainAvailable() throws IOException {
        String xml = read("src/main/res/layout/activity_onboarding.xml");
        assertTrue(xml.contains("onboarding_ident"));
        assertTrue(xml.contains("onboarding_realname"));
        assertTrue(xml.contains("onboarding_sasl_user"));
        assertTrue(xml.contains("onboarding_sasl_password"));
        assertTrue(xml.contains("onboarding_apply_existing"));
    }

    @Test
    public void networkCatalogueHasNonDestructivePickerMode() throws IOException {
        String java = read("src/main/java/io/mrarm/irc/NetworkCatalogActivity.java");
        assertTrue(java.contains("ARG_PICK_ONLY"));
        assertTrue(java.contains("RESULT_MANUAL"));
        assertTrue(java.contains("RESULT_ADDRESSES"));
        assertTrue(java.contains("if (mPickOnly)"));
    }

    @Test
    public void saslUsernameTracksNicknameUntilUserEditsIt() throws IOException {
        String java = read("src/main/java/io/mrarm/irc/onboarding/OnboardingActivity.java");
        assertTrue(java.contains("mSaslUserTouched"));
        assertTrue(java.contains("syncSaslUserFromNickname"));
        assertTrue(java.contains("if (!mSyncingSaslUser)"));
    }

    @Test
    public void interfaceSettingsCanRelaunchWizardWithConfirmation() throws IOException {
        String injector = read("src/main/java/io/mrarm/irc/setting/fragment/OnboardingSettingsInjector.java");
        String list = read("src/main/java/io/mrarm/irc/setting/fragment/SettingsListFragment.java");
        assertTrue(injector.contains("onboarding_reset_confirm_title"));
        assertTrue(injector.contains("OnboardingActivity.createIntent"));
        assertTrue(list.contains("OnboardingSettingsInjector.inject"));
    }

    @Test
    public void autoLaunchRequiresFreshUnconfiguredInstall() throws IOException {
        String state = read("src/main/java/io/mrarm/irc/onboarding/OnboardingState.java");
        assertTrue(state.contains("if (!isFreshInstall(context))"));
        assertTrue(state.contains("hasMeaningfulUserConfiguration"));
        assertTrue(state.contains("PREF_COMPLETED"));
        assertTrue(state.contains("SIMOSNAP_UUID"));
    }

    private static String read(String relative) throws IOException {
        Path path = Paths.get(relative);
        if (!Files.exists(path))
            path = Paths.get("app").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

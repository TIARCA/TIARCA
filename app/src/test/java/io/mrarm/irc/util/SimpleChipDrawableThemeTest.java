package io.mrarm.irc.util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SimpleChipDrawableThemeTest {

    @Test
    public void editorChipTextUsesDayNightHighContrastColor() {
        Context base = ApplicationProvider.getApplicationContext();
        Context light = contextWithNightMode(base, Configuration.UI_MODE_NIGHT_NO);
        Context dark = contextWithNightMode(base, Configuration.UI_MODE_NIGHT_YES);

        SimpleChipDrawable lightChip = new SimpleChipDrawable(light, "tempo", false);
        SimpleChipDrawable darkChip = new SimpleChipDrawable(dark, "tempo", false);

        assertEquals(ContextCompat.getColor(light, R.color.messageFormatEditorControlText),
                lightChip.getPaint().getColor());
        assertEquals(ContextCompat.getColor(dark, R.color.messageFormatEditorControlText),
                darkChip.getPaint().getColor());
        assertNotEquals(lightChip.getPaint().getColor(), darkChip.getPaint().getColor());
    }

    private Context contextWithNightMode(Context base, int nightMode) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | nightMode;
        return base.createConfigurationContext(configuration);
    }
}

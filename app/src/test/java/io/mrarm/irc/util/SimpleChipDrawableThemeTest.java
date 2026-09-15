package io.mrarm.irc.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

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
    public void messageFormatEditorChipsUseWhiteTextInDayAndNight() {
        Context base = ApplicationProvider.getApplicationContext();
        Context light = contextWithNightMode(base, Configuration.UI_MODE_NIGHT_NO);
        Context dark = contextWithNightMode(base, Configuration.UI_MODE_NIGHT_YES);

        SimpleChipDrawable lightChip = new SimpleChipDrawable(
                light, "tempo", null, false, true);
        SimpleChipDrawable darkChip = new SimpleChipDrawable(
                dark, "tempo", null, false, true);

        assertEquals(Color.WHITE, lightChip.getPaint().getColor());
        assertEquals(Color.WHITE, darkChip.getPaint().getColor());
        assertEquals(Color.rgb(63, 81, 181),
                ContextCompat.getColor(light, R.color.messageFormatEditorChipBackground));
        assertEquals(Color.rgb(36, 75, 115),
                ContextCompat.getColor(dark, R.color.messageFormatEditorChipBackground));
    }

    @Test
    public void genericLightChipIsNotForcedToEditorWhite() {
        Context base = ApplicationProvider.getApplicationContext();
        Context light = contextWithNightMode(base, Configuration.UI_MODE_NIGHT_NO);

        SimpleChipDrawable genericChip = new SimpleChipDrawable(light, "generic", false);

        assertNotEquals(Color.WHITE, genericChip.getPaint().getColor());
    }

    private Context contextWithNightMode(Context base, int nightMode) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | nightMode;
        return base.createConfigurationContext(configuration);
    }
}

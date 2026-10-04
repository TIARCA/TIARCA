package io.mrarm.irc.config;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import io.mrarm.irc.util.DefaultPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TmdbKeyPersistenceTest {
    @Test
    public void absentOrClearedKeyHasNoEmbeddedFallback() {
        Context context = ApplicationProvider.getApplicationContext();
        DefaultPreferences.get(context).edit().remove(QuickCommandSettings.PREF_TMDB_KEY).commit();
        assertEquals("", QuickCommandSettings.getTmdbKey(context));
        DefaultPreferences.get(context).edit().putString(QuickCommandSettings.PREF_TMDB_KEY, "  ").commit();
        assertEquals("", QuickCommandSettings.getTmdbKey(context));
    }

    @Test
    public void readingExistingPersonalKeyDoesNotRewriteUserData() {
        Context context = ApplicationProvider.getApplicationContext();
        DefaultPreferences.get(context).edit()
                .putString(QuickCommandSettings.PREF_TMDB_KEY, " personal-test-key ").commit();
        assertEquals("personal-test-key", QuickCommandSettings.getTmdbKey(context));
        assertEquals(" personal-test-key ", DefaultPreferences.get(context)
                .getString(QuickCommandSettings.PREF_TMDB_KEY, ""));
    }
}

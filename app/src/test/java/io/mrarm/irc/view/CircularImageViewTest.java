package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CircularImageViewTest {

    @Test
    public void awayIndicatorCanBeEnabledAndResetOnRecycledAvatar() {
        Context app = ApplicationProvider.getApplicationContext();
        Context context = new ContextThemeWrapper(app, R.style.AppTheme);
        CircularImageView avatar = new CircularImageView(context);
        avatar.setImageDrawable(new ColorDrawable(Color.BLUE));
        avatar.layout(0, 0, 64, 64);

        assertFalse(avatar.isAwayIndicatorVisible());

        avatar.setAwayIndicatorVisible(true);
        assertTrue(avatar.isAwayIndicatorVisible());
        draw(avatar);

        avatar.setAwayIndicatorVisible(false);
        assertFalse(avatar.isAwayIndicatorVisible());
        draw(avatar);
    }

    private static void draw(CircularImageView view) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
    }
}

package io.mrarm.irc.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.irc.R;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CircularImageViewTest {

    @Test
    public void awayBadgeAppearsAndDisappearsWithoutChangingAvatar() {
        Context app = ApplicationProvider.getApplicationContext();
        Context context = new ContextThemeWrapper(app, R.style.AppTheme);
        CircularImageView avatar = new CircularImageView(context);
        avatar.setImageDrawable(new ColorDrawable(Color.BLUE));
        avatar.layout(0, 0, 64, 64);

        assertEquals(Color.BLUE, draw(avatar).getPixel(54, 54));

        avatar.setAwayIndicatorVisible(true);
        Bitmap withBadge = draw(avatar);
        int orange = ContextCompat.getColor(context, R.color.userAwayColorPrimary);
        org.junit.Assert.assertTrue("AWAY color missing; center pixel="
                + Integer.toHexString(withBadge.getPixel(54, 54)),
                containsPixel(withBadge, orange));

        avatar.setAwayIndicatorVisible(false);
        Bitmap withoutBadge = draw(avatar);
        assertEquals(Color.BLUE, withoutBadge.getPixel(54, 54));
        org.junit.Assert.assertFalse(containsPixel(withoutBadge, orange));
    }

    private static boolean containsPixel(Bitmap bitmap, int color) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color)
                    return true;
            }
        }
        return false;
    }

    private static Bitmap draw(CircularImageView view) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}

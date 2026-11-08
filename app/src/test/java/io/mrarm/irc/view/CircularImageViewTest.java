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
        assertEquals(ContextCompat.getColor(context, R.color.userAwayColorPrimary),
                draw(avatar).getPixel(54, 54));

        avatar.setAwayIndicatorVisible(false);
        assertEquals(Color.BLUE, draw(avatar).getPixel(54, 54));
    }

    private static Bitmap draw(CircularImageView view) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}

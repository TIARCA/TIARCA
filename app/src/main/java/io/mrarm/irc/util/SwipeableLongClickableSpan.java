package io.mrarm.irc.util;

import android.view.View;

import androidx.annotation.NonNull;

/** A nickname span that also handles deliberate horizontal swipes. */
public abstract class SwipeableLongClickableSpan extends LongClickableSpan {

    /** Action configured for this direction; also used for the live visual preview. */
    public abstract String getSwipeAction(boolean right);

    /** @return true when the requested action was performed. */
    public abstract boolean onSwipe(@NonNull View widget, boolean right);
}

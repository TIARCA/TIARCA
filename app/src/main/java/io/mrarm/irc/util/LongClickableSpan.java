package io.mrarm.irc.util;

import android.view.View;

import androidx.annotation.NonNull;

import android.text.style.ClickableSpan;

/** A clickable text span with an optional, distinct long-press action. */
public abstract class LongClickableSpan extends ClickableSpan {

    /** Called after a long press directly on this span. */
    public abstract boolean onLongClick(@NonNull View widget);
}

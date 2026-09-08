package io.mrarm.irc.chat;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView used by chat and server-status message lists.
 *
 * The message adapters occasionally need a full data-set refresh (for example when the bounded
 * live-message window drops old rows). RecyclerView's default item animator interprets that
 * refresh as changes to every visible stable-id holder and fades the whole viewport, which looks
 * like the channel briefly disappearing. Message rows do not need transition animations, so keep
 * them disabled while leaving layout, scrolling and adapter update semantics unchanged.
 */
public class ChatMessagesRecyclerView extends RecyclerView {

    public ChatMessagesRecyclerView(Context context) {
        super(context);
        disableItemAnimations();
    }

    public ChatMessagesRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        disableItemAnimations();
    }

    public ChatMessagesRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        disableItemAnimations();
    }

    private void disableItemAnimations() {
        setItemAnimator(null);
    }
}

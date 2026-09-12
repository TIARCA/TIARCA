package io.mrarm.irc.view;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.Fragment;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.chat.ChatFragment;
import io.mrarm.irc.irc.AwayStateManager;

/** A compact status row shown above the composer only while the server confirms we are away. */
public class AwayStatusView extends AppCompatTextView implements AwayStateManager.Listener {

    private AwayStateManager manager;

    public AwayStatusView(Context context) {
        super(context);
        init();
    }

    public AwayStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AwayStatusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setVisibility(View.GONE);
        setOnClickListener(v -> {
            if (manager != null && manager.isAway())
                manager.requestAway(false, null, false);
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(this::bindToCurrentConnection);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (manager != null)
            manager.removeListener(this);
        manager = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE)
            post(this::bindToCurrentConnection);
    }

    private void bindToCurrentConnection() {
        MainActivity activity = findMainActivity(getContext());
        if (activity == null)
            return;
        Fragment current = activity.getCurrentFragment();
        if (!(current instanceof ChatFragment))
            return;
        AwayStateManager newManager = AwayStateManager.get(
                ((ChatFragment) current).getConnectionInfo());
        if (manager != newManager) {
            if (manager != null)
                manager.removeListener(this);
            manager = newManager;
            manager.addListener(this);
        }
        render();
    }

    @Override
    public void onAwayStateChanged(AwayStateManager manager) {
        if (this.manager == manager)
            render();
    }

    private void render() {
        if (manager == null || !manager.isAway()) {
            setVisibility(View.GONE);
            return;
        }
        String message = manager.getAwayMessage();
        if (message == null || message.isEmpty())
            setText(R.string.away_status);
        else
            setText(getResources().getString(R.string.away_status_with_message, message));
        setVisibility(View.VISIBLE);
    }

    private static MainActivity findMainActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof MainActivity)
                return (MainActivity) current;
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current)
                break;
            current = base;
        }
        return current instanceof MainActivity ? (MainActivity) current : null;
    }
}

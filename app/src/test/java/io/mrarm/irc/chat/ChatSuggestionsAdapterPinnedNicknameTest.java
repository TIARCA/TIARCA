package io.mrarm.irc.chat;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.irc.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChatSuggestionsAdapterPinnedNicknameTest {

    private ChatSuggestionsAdapter createAdapter() {
        Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
        return new ChatSuggestionsAdapter(context, null, null);
    }

    @Test
    public void uniqueCompletedNicknameStaysVisibleInSuggestionList() {
        ChatSuggestionsAdapter adapter = createAdapter();
        NickWithPrefix nick = new NickWithPrefix("hiroo", null);

        assertEquals(0, adapter.getItemCount());
        adapter.pinNickname(nick);

        assertEquals(1, adapter.getItemCount());
        assertSame(nick, adapter.getItem(0));
        assertSame(nick, adapter.getItem(0));

        // The pinned row goes through the ordinary nickname ViewHolder, which
        // already handles taps and the shared long-press context menu.
        ViewGroup parent = new FrameLayout(
                new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme));
        ChatSuggestionsAdapter.ItemHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        assertSame(nick, holder.itemView.getTag());
    }

    @Test
    public void clearingPinnedNicknameRestoresNormalResults() {
        ChatSuggestionsAdapter adapter = createAdapter();
        NickWithPrefix nick = new NickWithPrefix("hiroo", null);
        adapter.pinNickname(nick);

        adapter.clearPinnedNickname();

        assertEquals(0, adapter.getItemCount());
        assertEquals(false, adapter.hasPinnedNickname());
        assertEquals(false, adapter.isPinnedNickname(nick));
    }
}

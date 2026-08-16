package io.mrarm.irc.dialog;

import android.content.Context;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.view.ListSearchView;
import io.mrarm.chatlib.dto.MessageList;

public class UserSearchDialog extends SearchDialog {

    private ServerConnectionInfo mConnection;
    private ListSearchView.SimpleSuggestionsAdapter mAdapter;

    public UserSearchDialog(@NonNull Context context, ServerConnectionInfo connection) {
        super(context);
        mConnection = connection;
        setQueryHint(context.getString(R.string.action_message_user));
        mAdapter = new ListSearchView.SimpleSuggestionsAdapter();
        mAdapter.setItemClickListener((int index, CharSequence value) -> {
            onQueryTextSubmit(value.toString());
        });
        setSuggestionsAdapter(mAdapter);
    }

    @Override
    public void onQueryTextSubmit(String query) {
        query = query == null ? "" : query.trim();
        if (query.isEmpty())
            return;
        final String target = query;
        if (mConnection.hasChannel(target)) {
            openConversation(target);
            cancel();
            return;
        }
        mConnection.getApiInstance().getMessageStorageApi().getMessages(target, 1,
                null, null, (MessageList messages) -> {
                    if (messages != null && !messages.getMessages().isEmpty()) {
                        mConnection.addStoredConversation(target);
                        openConversation(target);
                    } else {
                        openOnlineConversation(target);
                    }
                }, error -> openOnlineConversation(target));
        cancel();
    }

    private void openOnlineConversation(String query) {
        List<String> channels = new ArrayList<>();
        channels.add(query);
        mConnection.getApiInstance().joinChannels(channels, (Void v) -> {
            openConversation(query);
        }, null);
    }

    private void openConversation(String query) {
        if (getOwnerActivity() == null)
            return;
        getOwnerActivity().runOnUiThread(() ->
                ((MainActivity) getOwnerActivity()).openServer(mConnection, query));
    }

    @Override
    public void onQueryTextChange(String newText) {
        if (newText.length() < 2) {
            mAdapter.setItems(null);
            return;
        }
        mConnection.getApiInstance().getUserInfoApi().findUsers(newText, (List<String> users) -> {
            List<CharSequence> suggestions = new ArrayList<>();
            for (String sug : users) {
                suggestions.add(sug);
            }
            mAdapter.setItems(suggestions);
        }, null);
    }

}

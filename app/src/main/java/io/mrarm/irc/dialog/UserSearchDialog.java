package io.mrarm.irc.dialog;

import android.content.Context;
import android.widget.Toast;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.chatlib.dto.WhoisInfo;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.irc.CallerIdAcceptManager;
import io.mrarm.irc.view.ListSearchView;

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
        verifyAndOpenOnlineConversation(target);
    }

    private void verifyAndOpenOnlineConversation(String query) {
        if (mConnection == null || mConnection.getApiInstance() == null)
            return;
        mConnection.getApiInstance().sendWhois(query, (WhoisInfo info) -> {
            String confirmedNick = info != null && info.getNick() != null ? info.getNick() : query;
            CallerIdAcceptManager.acceptOutgoingPrivateConversation(mConnection, confirmedNick);
            mConnection.registerPrivateConversation(confirmedNick, () -> {
                openConversation(confirmedNick);
                if (getOwnerActivity() != null)
                    getOwnerActivity().runOnUiThread(this::cancel);
            });
        }, error -> {
            if (getOwnerActivity() != null) {
                getOwnerActivity().runOnUiThread(() -> Toast.makeText(getContext(),
                        R.string.user_not_online, Toast.LENGTH_SHORT).show());
            }
        });
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
        // Suggestions intentionally stay local/known. A manually entered nickname is verified
        // against the server on submit instead of being rejected because it is absent here.
        mConnection.getApiInstance().getUserInfoApi().findUsers(newText, (List<String> users) -> {
            List<CharSequence> suggestions = new ArrayList<>();
            for (String sug : users) {
                suggestions.add(sug);
            }
            mAdapter.setItems(suggestions);
        }, null);
    }
}

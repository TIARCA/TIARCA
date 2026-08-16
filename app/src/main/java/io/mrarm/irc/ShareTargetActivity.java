package io.mrarm.irc;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;

import androidx.core.app.ActivityOptionsCompat;

import io.mrarm.irc.dialog.ChannelSearchDialog;
import io.mrarm.irc.util.StyledAttributesHelper;
import io.mrarm.irc.view.ListSearchView;

public class ShareTargetActivity extends ThemedActivity implements ListSearchView.QueryListener {

    private ChannelSearchDialog.SuggestionsAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DirectShareManager.applyTarget(getIntent());
        if (getIntent().hasExtra(MainActivity.ARG_SERVER_UUID) &&
                getIntent().hasExtra(MainActivity.ARG_CHANNEL_NAME)) {
            openDirectTarget();
            return;
        }

        mAdapter = new ChannelSearchDialog.SuggestionsAdapter(this);
        ListSearchView view = new ListSearchView(this, this);
        view.setBackgroundColor(StyledAttributesHelper.getColor(this, R.attr.colorBackgroundFloating, 0));
        view.setSuggestionsAdapter(mAdapter);
        setContentView(view);

        mAdapter.setItemClickListener((int index, Pair<ServerConnectionInfo, String> value) -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.ARG_SERVER_UUID, value.first.getUUID().toString());
            intent.putExtra(MainActivity.ARG_CHANNEL_NAME, value.second);
            intent.putExtra(Intent.EXTRA_TEXT, getIntent().getStringExtra(Intent.EXTRA_TEXT));
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(this,
                    android.R.anim.fade_in, android.R.anim.fade_out);
            startActivity(intent, options.toBundle());
            finish();
        });
    }

    private void openDirectTarget() {
        Intent intent = new Intent(getIntent());
        intent.setClass(this, MainActivity.class);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(this,
                android.R.anim.fade_in, android.R.anim.fade_out);
        startActivity(intent, options.toBundle());
        finish();
    }

    @Override
    public void onQueryTextChange(String newQuery) {
        mAdapter.filterWithQuery(newQuery);
    }

    @Override
    public void onQueryTextSubmit(String query) {
    }

    @Override
    public void onCancelled() {
        finish();
    }

}

package io.mrarm.irc.setting.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.mrarm.irc.R;
import io.mrarm.irc.setting.SettingsListAdapter;

public abstract class SettingsListFragment extends Fragment {

    private static final String STATE_PENDING_SETTINGS_REQUEST =
            "pending_settings_activity_request";
    private RecyclerView mRecyclerView;
    private RecyclerView.ItemDecoration mItemDecoration;
    private SettingsListAdapter mAdapter;
    private ActivityResultLauncher<Intent> mSettingsActivityLauncher;
    private int mPendingSettingsRequestCode;

    public abstract SettingsListAdapter createAdapter();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            mPendingSettingsRequestCode = savedInstanceState.getInt(
                    STATE_PENDING_SETTINGS_REQUEST);
        mSettingsActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (mAdapter != null)
                        mAdapter.dispatchActivityResult(mPendingSettingsRequestCode,
                                result.getResultCode(), result.getData());
                });
    }

    public final void launchSettingsActivity(Intent intent, int requestCode) {
        mPendingSettingsRequestCode = requestCode;
        mSettingsActivityLauncher.launch(intent);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PENDING_SETTINGS_REQUEST, mPendingSettingsRequestCode);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        if (mAdapter == null)
            mAdapter = createAdapter();
        View view = inflater.inflate(R.layout.simple_list, container, false);
        mRecyclerView = view.findViewById(R.id.items);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);
        mItemDecoration = mAdapter.createItemDecoration();
        mRecyclerView.addItemDecoration(mItemDecoration);
        return view;
    }

    public final void recreateAdapter() {
        if (mItemDecoration != null)
            mRecyclerView.removeItemDecoration(mItemDecoration);
        mAdapter = createAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mItemDecoration = mAdapter.createItemDecoration();
        mRecyclerView.addItemDecoration(mItemDecoration);
    }

}

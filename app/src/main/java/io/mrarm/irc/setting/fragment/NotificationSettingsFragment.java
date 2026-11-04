package io.mrarm.irc.setting.fragment;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.mrarm.irc.EditNotificationSettingsActivity;
import io.mrarm.irc.NotificationRulesAdapter;
import io.mrarm.irc.R;
import io.mrarm.irc.config.InAppAlertSettings;
import io.mrarm.irc.config.NotificationRuleManager;
import io.mrarm.irc.config.SettingsHelper;

public class NotificationSettingsFragment extends Fragment implements NamedSettingsFragment {

    private NotificationRulesAdapter mAdapter;

    @Override
    public String getName() {
        return getString(R.string.pref_header_notifications);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification_settings, container, false);
        SwitchCompat enabled = view.findViewById(R.id.in_app_alerts_enabled);
        SwitchCompat privateMessages = view.findViewById(R.id.in_app_alerts_private);
        SwitchCompat mentions = view.findViewById(R.id.in_app_alerts_mentions);
        SwitchCompat sound = view.findViewById(R.id.in_app_alerts_sound);

        enabled.setChecked(InAppAlertSettings.isEnabled());
        privateMessages.setChecked(InAppAlertSettings.isPrivateMessagesEnabled());
        mentions.setChecked(InAppAlertSettings.isMentionsEnabled());
        sound.setChecked(InAppAlertSettings.isSoundEnabled());

        privateMessages.setEnabled(enabled.isChecked());
        mentions.setEnabled(enabled.isChecked());
        sound.setEnabled(enabled.isChecked());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            SettingsHelper.getPreferences().edit()
                    .putBoolean(InAppAlertSettings.PREF_ENABLED, checked).apply();
            privateMessages.setEnabled(checked);
            mentions.setEnabled(checked);
            sound.setEnabled(checked);
        });
        privateMessages.setOnCheckedChangeListener((button, checked) ->
                SettingsHelper.getPreferences().edit()
                        .putBoolean(InAppAlertSettings.PREF_PRIVATE_MESSAGES, checked).apply());
        mentions.setOnCheckedChangeListener((button, checked) ->
                SettingsHelper.getPreferences().edit()
                        .putBoolean(InAppAlertSettings.PREF_MENTIONS, checked).apply());
        sound.setOnCheckedChangeListener((button, checked) ->
                SettingsHelper.getPreferences().edit()
                        .putBoolean(InAppAlertSettings.PREF_SOUND, checked).apply());

        RecyclerView recyclerView = view.findViewById(R.id.items);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new NotificationRulesAdapter(getActivity());
        recyclerView.setAdapter(mAdapter);
        recyclerView.addItemDecoration(mAdapter.createItemDecoration(getActivity()));
        mAdapter.enableDragDrop(recyclerView);

        View addButton = view.findViewById(R.id.add);
        addButton.setOnClickListener((View v) -> {
            startActivity(new Intent(getActivity(), EditNotificationSettingsActivity.class));
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        if (mAdapter != null && mAdapter.hasUnsavedChanges()) {
            NotificationRuleManager.saveUserRuleSettings(getActivity());
        }
        mAdapter = null;
        super.onDestroyView();
    }

}
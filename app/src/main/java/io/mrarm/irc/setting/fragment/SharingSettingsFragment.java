package io.mrarm.irc.setting.fragment;

import android.content.SharedPreferences;
import android.content.DialogInterface;
import io.mrarm.irc.util.DefaultPreferences;

import androidx.appcompat.app.AlertDialog;

import io.mrarm.irc.R;
import io.mrarm.irc.SettingsActivity;
import io.mrarm.irc.config.SharingSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;

/** Controls which attachment actions are exposed in private chats and WHOIS. */
public class SharingSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    @Override
    public String getName() {
        return getString(R.string.pref_header_sharing);
    }

    @Override
    public SettingsListAdapter createAdapter() {
        SettingsListAdapter adapter = new SettingsListAdapter(this);
        adapter.setRequestCodeCounter(((SettingsActivity) getActivity()).getRequestCodeCounter());
        SharedPreferences prefs = DefaultPreferences.get(getActivity());

        CheckBoxSetting uploads = setting(prefs, R.string.pref_sharing_uploads,
                R.string.pref_sharing_uploads_desc, SharingSettings.PREF_UPLOADS);
        adapter.add(uploads);
        adapter.add(new SettingsHeader(getString(R.string.pref_sharing_send_options)));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_pick_image,
                R.string.pref_sharing_pick_image_desc, SharingSettings.PREF_PICK_IMAGE));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_take_photo,
                R.string.pref_sharing_take_photo_desc, SharingSettings.PREF_TAKE_PHOTO));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_pick_video,
                R.string.pref_sharing_pick_video_desc, SharingSettings.PREF_PICK_VIDEO));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_record_video,
                R.string.pref_sharing_record_video_desc, SharingSettings.PREF_RECORD_VIDEO));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_pick_audio,
                R.string.pref_sharing_pick_audio_desc, SharingSettings.PREF_PICK_AUDIO));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_record_voice,
                R.string.pref_sharing_record_voice_desc, SharingSettings.PREF_RECORD_VOICE));
        addRequired(adapter, uploads, setting(prefs, R.string.pref_sharing_other_files,
                R.string.pref_sharing_other_files_desc, SharingSettings.PREF_OTHER_FILES));

        CheckBoxSetting dcc = new CheckBoxSetting(
                getString(R.string.pref_title_chat_show_dcc_send),
                getString(R.string.pref_summary_chat_show_dcc_send))
                .linkSetting(prefs, ChatSettings.PREF_SHOW_DCC_SEND);
        dcc.addListener(entry -> {
            if (((CheckBoxSetting) entry).isChecked())
                showDCCWarning((CheckBoxSetting) entry);
        });
        adapter.add(dcc);

        adapter.add(new SettingsHeader(getString(R.string.pref_sharing_viewing)));
        adapter.add(setting(prefs, R.string.pref_sharing_internal_viewer,
                R.string.pref_sharing_internal_viewer_desc,
                SharingSettings.PREF_INTERNAL_VIEWER));
        return adapter;
    }

    private CheckBoxSetting setting(SharedPreferences prefs, int title, int description,
                                    String key) {
        return new CheckBoxSetting(getString(title), getString(description), true)
                .linkPreference(prefs, key);
    }

    private void addRequired(SettingsListAdapter adapter, CheckBoxSetting requirement,
                             CheckBoxSetting setting) {
        setting.requires(requirement);
        adapter.add(setting);
    }

    private void showDCCWarning(CheckBoxSetting setting) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.dcc_enable_send_warning_title)
                .setMessage(R.string.dcc_enable_send_warning_body)
                .setPositiveButton(R.string.dcc_approve_download_enable_anyway, null)
                .setNegativeButton(R.string.action_cancel,
                        (DialogInterface dialog, int which) -> setting.setChecked(false))
                .setOnCancelListener(dialog -> setting.setChecked(false))
                .show();
    }
}

package io.mrarm.irc.setting.fragment;

import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;
import android.widget.Toast;

import io.mrarm.irc.R;
import io.mrarm.irc.config.QuickCommandSettings;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.EditTextSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;
import io.mrarm.irc.setting.SecretEditTextSetting;

/** Enables and renames the locally evaluated ! commands. */
public class QuickCommandSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    private SharedPreferences mPrefs;
    private boolean mValidatingTrigger;

    @Override
    public String getName() {
        return getString(R.string.pref_header_quick_commands);
    }

    @Override
    public SettingsListAdapter createAdapter() {
        SettingsListAdapter adapter = new SettingsListAdapter(this);
        mPrefs = DefaultPreferences.get(getActivity());
        CheckBoxSetting master = new CheckBoxSetting(getString(R.string.pref_quick_commands_enabled),
                getString(R.string.pref_quick_commands_enabled_desc), true)
                .linkPreference(mPrefs, QuickCommandSettings.PREF_ENABLED);
        adapter.add(master);
        adapter.add(new SettingsHeader(getString(R.string.pref_quick_commands_words)));
        addCommand(adapter, master, QuickCommandSettings.Command.YOUTUBE,
                R.string.pref_quick_command_youtube);
        addCommand(adapter, master, QuickCommandSettings.Command.WIKI,
                R.string.pref_quick_command_wiki);
        addCommand(adapter, master, QuickCommandSettings.Command.CALC,
                R.string.pref_quick_command_calc);
        addCommand(adapter, master, QuickCommandSettings.Command.MOVIE,
                R.string.pref_quick_command_movie);
        addCommand(adapter, master, QuickCommandSettings.Command.TIME,
                R.string.pref_quick_command_time);
        addCommand(adapter, master, QuickCommandSettings.Command.DICTIONARY,
                R.string.pref_quick_command_dictionary);
        adapter.add(new SettingsHeader(getString(R.string.pref_quick_commands_services)));
        SecretEditTextSetting key = new SecretEditTextSetting(getString(R.string.pref_tmdb_key),
                getString(R.string.pref_tmdb_key_hint));
        key.linkPreference(mPrefs, QuickCommandSettings.PREF_TMDB_KEY);
        key.requires(master);
        adapter.add(key);
        return adapter;
    }

    private void addCommand(SettingsListAdapter adapter, CheckBoxSetting master,
                            QuickCommandSettings.Command command, int title) {
        CheckBoxSetting enabled = new CheckBoxSetting(getString(title),
                getString(R.string.pref_quick_command_enable_desc, command.defaultTrigger), true)
                .linkPreference(mPrefs, command.enabledKey);
        enabled.requires(master);
        adapter.add(enabled);
        EditTextSetting trigger = new EditTextSetting(
                getString(R.string.pref_quick_command_word, getString(title)),
                command.defaultTrigger, command.defaultTrigger);
        trigger.linkPreference(mPrefs, command.triggerKey);
        trigger.requires(enabled);
        trigger.addListener(entry -> validateTrigger((EditTextSetting) entry, command));
        adapter.add(trigger);
    }

    private void validateTrigger(EditTextSetting setting, QuickCommandSettings.Command command) {
        if (mValidatingTrigger) return;
        String normalized = QuickCommandSettings.normalizeTrigger(setting.getText(),
                command.defaultTrigger);
        if (!normalized.equals(setting.getText())) {
            mValidatingTrigger = true;
            setting.setText(normalized);
            mValidatingTrigger = false;
            Toast.makeText(getContext(), R.string.pref_quick_command_word_normalized,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        for (QuickCommandSettings.Command other : QuickCommandSettings.Command.values()) {
            if (other == command) continue;
            String otherValue = QuickCommandSettings.normalizeTrigger(
                    mPrefs.getString(other.triggerKey, other.defaultTrigger), other.defaultTrigger);
            if (normalized.equals(otherValue)) {
                mValidatingTrigger = true;
                setting.setText(command.defaultTrigger);
                mValidatingTrigger = false;
                Toast.makeText(getContext(), R.string.pref_quick_command_word_duplicate,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
    }
}

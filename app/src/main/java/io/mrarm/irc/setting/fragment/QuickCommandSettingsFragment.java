package io.mrarm.irc.setting.fragment;

import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;
import android.content.Intent;
import android.net.Uri;

import io.mrarm.irc.R;
import io.mrarm.irc.config.QuickCommandSettings;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.ClickableSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;
import io.mrarm.irc.setting.SecretEditTextSetting;

/** Enables the local ! commands and explains their usage without trigger editors. */
public class QuickCommandSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    private SharedPreferences mPrefs;

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
        adapter.add(new SettingsHeader(getString(R.string.pref_quick_commands_usage)));
        addCommand(adapter, master, QuickCommandSettings.Command.YOUTUBE,
                R.string.pref_quick_command_youtube, R.string.pref_quick_youtube_usage);
        addCommand(adapter, master, QuickCommandSettings.Command.WIKI,
                R.string.pref_quick_command_wiki, R.string.pref_quick_wiki_usage);
        addCommand(adapter, master, QuickCommandSettings.Command.CALC,
                R.string.pref_quick_command_calc, R.string.pref_quick_calc_usage);
        addCommand(adapter, master, QuickCommandSettings.Command.MOVIE,
                R.string.pref_quick_command_movie, R.string.pref_quick_movie_usage);
        addCommand(adapter, master, QuickCommandSettings.Command.TIME,
                R.string.pref_quick_command_time, R.string.pref_quick_time_usage);
        addCommand(adapter, master, QuickCommandSettings.Command.DICTIONARY,
                R.string.pref_quick_command_dictionary, R.string.pref_quick_dictionary_usage);
        adapter.add(new SettingsHeader(getString(R.string.pref_quick_commands_services)));
        SecretEditTextSetting key = new SecretEditTextSetting(getString(R.string.pref_tmdb_key),
                getString(R.string.pref_tmdb_key_hint));
        key.linkPreference(mPrefs, QuickCommandSettings.PREF_TMDB_KEY);
        key.requires(master);
        adapter.add(key);
        adapter.add(new ClickableSetting(getString(R.string.pref_tmdb_key_guide),
                getString(R.string.pref_tmdb_key_guide_desc)).setIntent(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://developer.themoviedb.org/docs/getting-started"))));
        return adapter;
    }

    private void addCommand(SettingsListAdapter adapter, CheckBoxSetting master,
                            QuickCommandSettings.Command command, int title, int description) {
        CheckBoxSetting enabled = new CheckBoxSetting(getString(title),
                getString(description, QuickCommandSettings.getTrigger(getContext(), command)), true)
                .linkPreference(mPrefs, command.enabledKey);
        enabled.requires(master);
        adapter.add(enabled);
    }
}

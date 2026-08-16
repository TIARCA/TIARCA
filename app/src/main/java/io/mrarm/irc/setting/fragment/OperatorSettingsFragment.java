package io.mrarm.irc.setting.fragment;

import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;

import io.mrarm.irc.R;
import io.mrarm.irc.config.OperatorReasonSettings;
import io.mrarm.irc.setting.EditTextSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;

/** Editable moderation reasons used by the operator WHOIS actions. */
public class OperatorSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    @Override
    public String getName() {
        return getString(R.string.pref_header_operator_actions);
    }

    @Override
    public SettingsListAdapter createAdapter() {
        SettingsListAdapter adapter = new SettingsListAdapter(this);
        SharedPreferences prefs = DefaultPreferences.get(getActivity());
        adapter.add(new SettingsHeader(getString(R.string.pref_operator_reasons_header)));
        for (int i = 0; i < OperatorReasonSettings.MAX_REASONS; i++) {
            EditTextSetting reason = new EditTextSetting(
                    getString(R.string.pref_operator_reason_number, i + 1),
                    OperatorReasonSettings.DEFAULT_REASONS[i],
                    getString(R.string.pref_operator_reason_empty));
            reason.linkPreference(prefs, OperatorReasonSettings.PREF_KEYS[i]);
            adapter.add(reason);
        }
        return adapter;
    }
}

package io.mrarm.irc.setting.fragment;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import io.mrarm.irc.R;
import io.mrarm.irc.onboarding.OnboardingActivity;
import io.mrarm.irc.setting.ClickableSetting;
import io.mrarm.irc.setting.SettingsListAdapter;

/** Adds the rerunnable initial-setup entry without coupling onboarding to Interface internals. */
final class OnboardingSettingsInjector {

    private OnboardingSettingsInjector() {
    }

    static void inject(SettingsListFragment fragment, SettingsListAdapter adapter) {
        if (!(fragment instanceof InterfaceSettingsFragment) || fragment.getContext() == null)
            return;
        adapter.add(new ClickableSetting(
                fragment.getString(R.string.onboarding_settings_title),
                fragment.getString(R.string.onboarding_settings_summary))
                .setOnClickListener((View view) -> new AlertDialog.Builder(fragment.requireContext())
                        .setTitle(R.string.onboarding_reset_confirm_title)
                        .setMessage(R.string.onboarding_reset_confirm_body)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.onboarding_reset_confirm_action,
                                (dialog, which) -> {
                                    Intent intent = OnboardingActivity.createIntent(
                                            fragment.requireContext(), true);
                                    fragment.startActivity(intent);
                                })
                        .show()));
    }
}

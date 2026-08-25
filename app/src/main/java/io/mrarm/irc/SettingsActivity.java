package io.mrarm.irc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import io.mrarm.irc.setting.SettingsCategoriesFragment;
import io.mrarm.irc.setting.fragment.CommandSettingsFragment;
import io.mrarm.irc.setting.fragment.InterfaceSettingsFragment;
import io.mrarm.irc.setting.fragment.NamedSettingsFragment;
import io.mrarm.irc.setting.fragment.NotificationSettingsFragment;
import io.mrarm.irc.setting.fragment.OperatorSettingsFragment;
import io.mrarm.irc.setting.fragment.ReconnectSettingsFragment;
import io.mrarm.irc.setting.fragment.QuickCommandSettingsFragment;
import io.mrarm.irc.setting.fragment.StorageSettingsFragment;
import io.mrarm.irc.setting.fragment.SharingSettingsFragment;
import io.mrarm.irc.setting.fragment.UserSettingsFragment;
import io.mrarm.irc.setup.BackupActivity;
import io.mrarm.irc.util.SimpleCounter;

public class SettingsActivity extends ThemedActivity {

    private SimpleCounter mRequestCodeCounter = new SimpleCounter(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // A compact AppCompat action bar reserves one line of layout height.  On Android
        // 16, adding a subtitle makes it render as two lines while the content still starts
        // after a single line, covering the first setting.  The version remains available in
        // "About TIARCA"; keep this navigation bar single-line on every supported version.
        getSupportActionBar().setSubtitle(null);

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(FragmentManager fragmentManager,
                                                      Fragment fragment, View view,
                                                      Bundle savedInstanceState) {
                        if (fragment.getId() == R.id.content_frame)
                            updateTitle();
                    }
                }, false);
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            updateTitle();
        });
        if (getSupportFragmentManager().findFragmentById(R.id.content_frame) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, CategoriesFragment.newInstance())
                    .commit();
        }
    }

    public SimpleCounter getRequestCodeCounter() {
        return mRequestCodeCounter;
    }

    public void updateTitle() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (fragment == null)
            return;
        if (fragment instanceof NamedSettingsFragment)
            getSupportActionBar().setTitle(((NamedSettingsFragment) fragment).getName());
        else
            getSupportActionBar().setTitle(R.string.title_activity_settings);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit();
    }

    public static class CategoriesFragment extends SettingsCategoriesFragment {

        public static CategoriesFragment newInstance() {
            return new CategoriesFragment();
        }

        @Override
        public List<Item> getItems() {
            List<Item> ret = new ArrayList<>();
            ret.add(new Item(R.string.pref_header_user, R.drawable.ic_user, UserSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_reconnect, R.drawable.ic_refresh, ReconnectSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_interface, R.drawable.ic_appearance, InterfaceSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_notifications, R.drawable.ic_notifications, NotificationSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_command_aliases, R.drawable.ic_command_aliases, CommandSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_quick_commands, R.drawable.ic_quick_commands,
                    QuickCommandSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_operator_actions, R.drawable.ic_operator_actions,
                    OperatorSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_storage, R.drawable.ic_storage, StorageSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_sharing, R.drawable.ic_sharing_media,
                    SharingSettingsFragment.class));
            ret.add(new Item(R.string.pref_header_backup, R.drawable.ic_settings_backup, (View v) -> {
                v.getContext().startActivity(new Intent(v.getContext(), BackupActivity.class));
            }));
            ret.add(new Item(R.string.pref_header_about, R.drawable.ic_info, (View v) ->
                    new AlertDialog.Builder(v.getContext())
                            .setTitle(R.string.about_title)
                            .setMessage(v.getContext().getString(R.string.about_body,
                                    BuildConfig.VERSION_NAME))
                            .setNeutralButton(R.string.about_revolution_github, (dialog, which) ->
                                    v.getContext().startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://github.com/MCMrARM/revolution-irc"))))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()));
            return ret;
        }

    }





}

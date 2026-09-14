package io.mrarm.irc.setting.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.CompoundButtonCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.irc.MessageFormatSettingsActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.SettingsActivity;
import io.mrarm.irc.ThemeEditorActivity;
import io.mrarm.irc.ThemedActivity;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.EventDisplaySettings;
import io.mrarm.irc.dialog.MenuBottomSheetDialog;
import io.mrarm.irc.setting.ChatBackgroundSetting;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.ClickableSetting;
import io.mrarm.irc.setting.FontSizeSetting;
import io.mrarm.irc.setting.ListSetting;
import io.mrarm.irc.setting.ListWithCustomSetting;
import io.mrarm.irc.setting.RadioButtonSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;
import io.mrarm.irc.util.AppLocaleManager;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.EntryRecyclerViewAdapter;
import io.mrarm.irc.util.MessageBuilder;
import io.mrarm.irc.util.StyledAttributesHelper;
import io.mrarm.irc.util.theme.AppearancePreset;
import io.mrarm.irc.util.theme.AppearancePresetManager;
import io.mrarm.irc.util.theme.ThemeArchive;
import io.mrarm.irc.util.theme.ThemeInfo;
import io.mrarm.irc.util.theme.ThemeManager;
import io.mrarm.irc.util.theme.UserPresetStore;

public class InterfaceSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    private ClickableSetting mMessageFormatItem;
    private MessageInfo mSampleMessage;
    private ActivityResultLauncher<Intent> mThemeEditorLauncher;
    private ActivityResultLauncher<Intent> mImportThemeLauncher;
    private ActivityResultLauncher<Intent> mExportThemeLauncher;
    private ThemeInfo mPendingExportTheme;
    private boolean mPendingExportThemeIsTemporary;
    private final List<PresetOptionSetting> mPresetOptions = new ArrayList<>();
    private final List<UserPresetOptionSetting> mUserPresetOptions = new ArrayList<>();
    private boolean mUpdatingPresetSelection;
    private SharedPreferences mPresetPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mPresetListener =
            (preferences, key) -> {
                if (AppearancePresetManager.PREF_APPEARANCE_PRESET.equals(key)
                        || AppSettings.PREF_THEME.equals(key))
                    updatePresetSelection();
            };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mThemeEditorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    // Theme changes are reflected by onResume and the live theme manager.
                });
        mImportThemeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result ->
                        importTheme(result.getData()));
        mExportThemeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result ->
                        exportThemeTo(result.getData()));
        setHasOptionsMenu(true);
    }

    @Override
    public String getName() {
        return getString(R.string.pref_header_interface);
    }

    @Override
    public SettingsListAdapter createAdapter() {
        SettingsListAdapter a = new SettingsListAdapter(this);
        SharedPreferences prefs = DefaultPreferences.get(getActivity());
        a.setRequestCodeCounter(((SettingsActivity) getActivity()).getRequestCodeCounter());
        a.add(new SettingsHeader(getString(R.string.pref_header_language)));
        a.add(new ListSetting(getString(R.string.pref_title_language),
                getResources().getStringArray(R.array.pref_entries_language_all),
                getResources().getStringArray(R.array.pref_entry_values_language_all),
                AppLocaleManager.getLanguage(prefs))
                .linkPreference(prefs, AppLocaleManager.PREF_APP_LANGUAGE)
                .addListener((EntryRecyclerViewAdapter.Entry entry) ->
                        AppLocaleManager.applyLanguage(
                                ((ListSetting) entry).getSelectedOptionValue())));
        a.add(new SettingsHeader(getString(R.string.pref_header_appearance_preset)));
        createPresetList(a);
        a.add(new SettingsHeader(getString(R.string.pref_header_theme)));
        createThemeList(a);
        a.add(new ClickableSetting(getString(R.string.theme_create_new), null)
                .setOnClickListener((View v) -> {
                    ThemeInfo newTheme = createNewTheme();
                    ThemeManager.getInstance(getContext()).setTheme(newTheme);
                    openThemeEditor(newTheme);
                    getActivity().recreate();
                }));
        a.add(new SettingsHeader(getString(R.string.pref_header_chat)));
        a.add(new ListWithCustomSetting(a, getString(R.string.pref_title_font),
                getResources().getStringArray(R.array.pref_entries_font),
                getResources().getStringArray(R.array.pref_entry_values_font), null,
                ChatSettings.PREF_FONT, ListWithCustomSetting.TYPE_FONT)
                .linkSetting(prefs, ChatSettings.PREF_FONT));
        a.add(new FontSizeSetting(getString(R.string.pref_title_font_size))
                .linkSetting(prefs, ChatSettings.PREF_FONT_SIZE));
        a.add(new ChatBackgroundSetting(a, getString(R.string.pref_title_chat_background)));
        a.add(new CheckBoxSetting(getString(R.string.pref_title_autocorrect),
                getString(R.string.pref_summary_autocorrect))
                .linkSetting(prefs, ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED));
        a.add(new CheckBoxSetting(getString(R.string.pref_title_monochrome_mode),
                getString(R.string.pref_summary_monochrome_mode))
                .linkSetting(prefs, EventDisplaySettings.PREF_MONOCHROME_MODE));
        a.add(new CheckBoxSetting(getString(R.string.pref_title_monochrome_kick),
                getString(R.string.pref_summary_monochrome_kick))
                .linkSetting(prefs, EventDisplaySettings.PREF_MONOCHROME_KICK));
        a.add(new CheckBoxSetting(getString(R.string.pref_title_monochrome_quit),
                getString(R.string.pref_summary_monochrome_quit))
                .linkSetting(prefs, EventDisplaySettings.PREF_MONOCHROME_QUIT));
        a.add(new CheckBoxSetting(getString(R.string.pref_title_monochrome_join_part),
                getString(R.string.pref_summary_monochrome_join_part))
                .linkSetting(prefs, EventDisplaySettings.PREF_MONOCHROME_JOIN_PART));
        a.add(new ListSetting(getString(R.string.pref_title_appbar_compact_mode),
                getResources().getStringArray(R.array.pref_entries_appbar_compact_mode),
                getResources().getStringArray(R.array.pref_entry_values_appbar_compact_mode))
                .linkSetting(prefs, ChatSettings.PREF_APPBAR_COMPACT_MODE));
        mMessageFormatItem = new ClickableSetting(getString(R.string.pref_title_message_format), null)
                .setIntent(new Intent(getActivity(), MessageFormatSettingsActivity.class));
        a.add(mMessageFormatItem);
        a.add(new CheckBoxSetting(getString(R.string.pref_title_chat_box_always_multiline),
                getString(R.string.pref_summary_chat_box_always_multiline))
                .linkSetting(prefs, ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE));

        MessageSenderInfo testSender = new MessageSenderInfo(
                getString(R.string.message_example_sender), "", "", null, null);
        Date date = MessageFormatSettingsActivity.getSampleMessageTime();
        mSampleMessage = new MessageInfo(testSender, date,
                getString(R.string.message_example_message), MessageInfo.MessageType.NORMAL);
        return a;
    }

    @Override
    public void onStart() {
        super.onStart();
        mPresetPreferences = DefaultPreferences.get(requireContext());
        mPresetPreferences.registerOnSharedPreferenceChangeListener(mPresetListener);
        updatePresetSelection();
        if (((ThemedActivity) getActivity()).hasThemeChanged()) {
            getActivity().recreate();
        }
    }

    @Override
    public void onStop() {
        if (mPresetPreferences != null) {
            mPresetPreferences.unregisterOnSharedPreferenceChangeListener(mPresetListener);
            mPresetPreferences = null;
        }
        super.onStop();
    }

    private void createPresetList(SettingsListAdapter adapter) {
        mPresetOptions.clear();
        mUserPresetOptions.clear();
        RadioButtonSetting.Group group = new RadioButtonSetting.Group();
        AppearancePresetManager manager = AppearancePresetManager.getInstance(requireContext());

        for (AppearancePreset preset : AppearancePreset.values()) {
            if (!preset.isApplicable())
                continue;
            PresetOptionSetting option = new PresetOptionSetting(
                    getString(preset.getNameResId()), group);
            option.linkPreset(this, manager, preset);
            mPresetOptions.add(option);
            adapter.add(option);
        }

        ThemeManager themeManager = ThemeManager.getInstance(requireContext());
        List<ThemeInfo> imported = new ArrayList<>();
        for (ThemeInfo theme : themeManager.getCustomThemes()) {
            if (UserPresetStore.contains(requireContext(), theme))
                imported.add(theme);
        }
        imported.sort(Comparator.comparing(
                theme -> theme.name == null ? "" : theme.name,
                String.CASE_INSENSITIVE_ORDER));
        for (ThemeInfo theme : imported) {
            String name = theme.name == null || theme.name.trim().isEmpty()
                    ? theme.uuid.toString() : theme.name;
            UserPresetOptionSetting option = new UserPresetOptionSetting(name, group)
                    .linkPreset(this, themeManager, theme);
            mUserPresetOptions.add(option);
            adapter.add(option);
        }

        PresetOptionSetting custom = new PresetOptionSetting(
                getString(AppearancePreset.CUSTOM.getNameResId()), group);
        custom.linkPreset(this, manager, AppearancePreset.CUSTOM);
        custom.setEnabled(false);
        mPresetOptions.add(custom);
        adapter.add(custom);
    }

    private void updatePresetSelection() {
        if (getContext() == null)
            return;
        ThemeManager themeManager = ThemeManager.getInstance(requireContext());
        ThemeInfo activeTheme = themeManager.getCurrentCustomTheme();
        boolean activeUserPreset = UserPresetStore.contains(requireContext(), activeTheme);
        AppearancePreset current = AppearancePresetManager.getInstance(requireContext())
                .getCurrentPreset();
        mUpdatingPresetSelection = true;
        try {
            for (PresetOptionSetting option : mPresetOptions)
                option.setChecked(!activeUserPreset && option.linkedPreset == current);
            for (UserPresetOptionSetting option : mUserPresetOptions)
                option.setChecked(activeUserPreset && option.linkedTheme == activeTheme);
        } finally {
            mUpdatingPresetSelection = false;
        }
    }

    private int[] getBaseThemeColors(int resId) {
        int[] colors = new int[3];
        StyledAttributesHelper attrs = StyledAttributesHelper.obtainStyledAttributes(getContext(),
                resId, new int[] { R.attr.colorPrimary, R.attr.colorPrimaryDark,
                        R.attr.colorAccent });
        colors[0] = attrs.getColor(R.attr.colorPrimary, 0);
        colors[1] = attrs.getColor(R.attr.colorPrimaryDark, 0);
        colors[2] = attrs.getColor(R.attr.colorAccent, 0);
        attrs.recycle();
        return colors;
    }

    private void createThemeList(SettingsListAdapter a) {
        ThemeManager themeManager = ThemeManager.getInstance(getContext());
        RadioButtonSetting.Group themeGroup = new RadioButtonSetting.Group();
        for (ThemeManager.BaseTheme theme : themeManager.getBaseThemes()) {
            int themeResId = theme.getThemeResId();
            a.add(new ThemeOptionSetting(getString(theme.getNameResId()),
                    themeGroup, getBaseThemeColors(themeResId))
                    .linkBaseTheme(this, theme));
        }
        for (ThemeInfo theme : themeManager.getCustomThemes()) {
            if (UserPresetStore.contains(requireContext(), theme))
                continue;
            int[] colors = getBaseThemeColors(theme.baseThemeInfo.getThemeResId());
            Integer c = theme.colors.get(ThemeInfo.COLOR_PRIMARY);
            if (c != null)
                colors[0] = c;
            c = theme.colors.get(ThemeInfo.COLOR_PRIMARY_DARK);
            if (c != null)
                colors[1] = c;
            c = theme.colors.get(ThemeInfo.COLOR_ACCENT);
            if (c != null)
                colors[2] = c;
            a.add(new ThemeOptionSetting(theme.name, themeGroup, colors)
                    .linkCustomTheme(this, theme));
        }
    }

    private void openThemeEditor(ThemeInfo theme) {
        Intent intent = new Intent(getContext(), ThemeEditorActivity.class);
        intent.putExtra(ThemeEditorActivity.ARG_THEME_UUID, theme.uuid.toString());
        mThemeEditorLauncher.launch(intent);
    }

    private ThemeInfo createNewTheme() {
        ThemeManager themeManager = ThemeManager.getInstance(getContext());
        ThemeInfo currentCustomTheme = themeManager.getCurrentCustomTheme();
        if (currentCustomTheme != null) {
            return createNewTheme(currentCustomTheme);
        } else {
            ThemeManager.ThemeResInfo currentTheme = themeManager.getCurrentTheme();
            if (!(currentTheme instanceof ThemeManager.BaseTheme))
                currentTheme = themeManager.getFallbackTheme();
            return createNewTheme((ThemeManager.BaseTheme) currentTheme);
        }
    }

    private ThemeInfo createNewTheme(ThemeManager.BaseTheme theme) {
        ThemeInfo newTheme = new ThemeInfo();
        newTheme.base = theme.getId();
        newTheme.baseThemeInfo = theme;
        newTheme.name = getString(R.string.theme_custom_default_name);
        initNewTheme(newTheme);
        return newTheme;
    }

    private ThemeInfo createNewTheme(ThemeInfo theme) {
        ThemeInfo newTheme = new ThemeInfo();
        newTheme.copyFrom(theme);
        newTheme.name = getString(R.string.value_copy, theme.name);
        initNewTheme(newTheme);
        return newTheme;
    }

    private void initNewTheme(ThemeInfo newTheme) {
        ThemeManager themeManager = ThemeManager.getInstance(getContext());
        try {
            themeManager.saveTheme(newTheme);
        } catch (IOException e) {
            Log.w("InterfaceSettings", "Failed to save new theme");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMessageFormatItem.setDescription(MessageBuilder.getInstance(getActivity())
                .buildMessage(mSampleMessage));
    }

    private void importTheme(Intent data) {
        if (data == null || data.getData() == null)
            return;
        ThemeManager manager = ThemeManager.getInstance(requireContext());
        Set<UUID> before = UserPresetStore.snapshotThemeIds(manager);
        try {
            Uri uri = data.getData();
            String displayName = getDisplayName(uri);
            try (ParcelFileDescriptor desc = requireActivity().getContentResolver()
                    .openFileDescriptor(uri, "r")) {
                if (desc == null)
                    throw new IOException("Unable to open theme");
                try (FileInputStream in = new FileInputStream(desc.getFileDescriptor())) {
                    manager.importTheme(in);
                }
            }
            ThemeInfo imported = UserPresetStore.findImportedTheme(manager, before);
            if (imported != null) {
                if (imported.name == null || imported.name.trim().isEmpty()) {
                    String fallback = UserPresetStore.nameFromFile(displayName);
                    if (fallback != null) {
                        imported.name = fallback;
                        manager.saveTheme(imported);
                    }
                }
                UserPresetStore.mark(requireContext(), imported);
            }
            // setTheme() already restored all preferences; recreate this Settings activity now so
            // its live theme/resources reflect the imported preset without leaving Settings.
            requireActivity().recreate();
        } catch (IOException e) {
            Log.w("InterfaceSettings", "Failed to import preset");
            Toast.makeText(getContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private String getDisplayName(Uri uri) {
        if (uri == null)
            return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = requireActivity().getContentResolver().query(uri,
                    new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String value = cursor.getString(index);
                        if (value != null && !value.trim().isEmpty())
                            return value;
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall back to the Uri segment below.
            }
        }
        return uri.getLastPathSegment();
    }

    private ThemeInfo prepareThemeForExport() {
        ThemeManager themeManager = ThemeManager.getInstance(getContext());
        ThemeInfo currentCustomTheme = themeManager.getCurrentCustomTheme();
        if (currentCustomTheme != null) {
            mPendingExportThemeIsTemporary = false;
            return currentCustomTheme;
        }

        ThemeManager.ThemeResInfo currentTheme = themeManager.getCurrentTheme();
        if (!(currentTheme instanceof ThemeManager.BaseTheme))
            currentTheme = themeManager.getFallbackTheme();
        ThemeManager.BaseTheme baseTheme = (ThemeManager.BaseTheme) currentTheme;

        ThemeInfo exportTheme = new ThemeInfo();
        exportTheme.base = baseTheme.getId();
        exportTheme.baseThemeInfo = baseTheme;
        AppearancePreset appearancePreset = AppearancePresetManager.getInstance(requireContext())
                .getCurrentPreset();
        exportTheme.name = appearancePreset.isApplicable()
                ? getString(appearancePreset.getNameResId())
                : getString(baseTheme.getNameResId());
        try {
            themeManager.saveTheme(exportTheme);
            mPendingExportThemeIsTemporary = true;
            return exportTheme;
        } catch (IOException e) {
            Log.w("InterfaceSettings", "Failed to prepare base theme export", e);
            return null;
        }
    }

    private void exportThemeTo(Intent data) {
        ThemeInfo theme = mPendingExportTheme;
        boolean temporary = mPendingExportThemeIsTemporary;
        mPendingExportTheme = null;
        mPendingExportThemeIsTemporary = false;

        if (data == null || data.getData() == null || theme == null) {
            if (temporary && theme != null)
                ThemeManager.getInstance(getContext()).deleteTheme(theme);
            return;
        }
        try {
            Uri uri = data.getData();
            try (ParcelFileDescriptor desc = requireActivity().getContentResolver()
                    .openFileDescriptor(uri, "w")) {
                if (desc == null)
                    throw new IOException("Unable to open theme destination");
                try (FileOutputStream out = new FileOutputStream(desc.getFileDescriptor())) {
                    ThemeManager.getInstance(getContext()).exportTheme(theme, out);
                }
            }
        } catch (IOException e) {
            Log.w("InterfaceSettings", "Failed to export preset");
            Toast.makeText(getContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        } finally {
            if (temporary)
                ThemeManager.getInstance(getContext()).deleteTheme(theme);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_settings_interface, menu);
        menu.findItem(R.id.action_import_theme).setOnMenuItemClickListener((i) -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            mImportThemeLauncher.launch(intent);
            return true;
        });
        MenuItem saveTheme = menu.findItem(R.id.action_save_theme);
        saveTheme.setEnabled(true);
        saveTheme.setOnMenuItemClickListener((i) -> {
            ThemeInfo theme = prepareThemeForExport();
            if (theme == null) {
                Toast.makeText(getContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
                return true;
            }
            mPendingExportTheme = theme;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(ThemeArchive.MIME_TYPE);
            intent.putExtra(Intent.EXTRA_TITLE, theme.name + ThemeArchive.FILE_EXTENSION);
            mExportThemeLauncher.launch(intent);
            return true;
        });
    }

    public static final class ThemeOptionSetting extends RadioButtonSetting {

        private static final int sHolder = SettingsListAdapter.registerViewHolder(Holder.class,
                R.layout.settings_theme_option);

        private InterfaceSettingsFragment fragment;
        private int[] overrideColors;
        private ThemeManager.BaseTheme linkedBaseTheme;
        private ThemeInfo linkedCustomTheme;

        public ThemeOptionSetting(String name, RadioButtonSetting.Group group,
                                  int[] overrideColors) {
            super(name, group);
            this.overrideColors = overrideColors;
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            if (checked) {
                if (linkedBaseTheme != null)
                    ThemeManager.getInstance(null).setTheme(linkedBaseTheme);
                if (linkedCustomTheme != null)
                    ThemeManager.getInstance(null).setTheme(linkedCustomTheme);
                if ((linkedBaseTheme != null || linkedCustomTheme != null) &&
                        fragment != null && fragment.getActivity() != null)
                    fragment.getActivity().recreate();
            }
        }

        public ThemeOptionSetting linkBaseTheme(InterfaceSettingsFragment fragment,
                                                ThemeManager.BaseTheme theme) {
            this.fragment = fragment;
            ThemeManager themeManager = ThemeManager.getInstance(null);
            setChecked(themeManager.getCurrentTheme() == theme ||
                    (themeManager.getCurrentTheme() == null &&
                            themeManager.getFallbackTheme() == theme));
            linkedBaseTheme = theme;
            return this;
        }

        public ThemeOptionSetting linkCustomTheme(InterfaceSettingsFragment fragment,
                                                  ThemeInfo theme) {
            this.fragment = fragment;
            setChecked(ThemeManager.getInstance(null).getCurrentCustomTheme() == theme);
            linkedCustomTheme = theme;
            return this;
        }

        @Override
        public int getViewHolder() {
            return sHolder;
        }

        public static class Holder extends RadioButtonSetting.Holder
                implements View.OnLongClickListener {

            private ColorStateList mDefaultButtonTintList;

            public Holder(View itemView, SettingsListAdapter adapter) {
                super(itemView, adapter);
                mDefaultButtonTintList = CompoundButtonCompat.getButtonTintList(mCheckBox);
                itemView.setOnLongClickListener(this);
            }

            @Override
            public void bind(CheckBoxSetting entry) {
                super.bind(entry);
                int[] overrideColors = ((ThemeOptionSetting) entry).overrideColors;
                int bgColor = StyledAttributesHelper.getColor(mCheckBox.getContext(),
                        android.R.attr.colorBackground, 0);
                boolean darkBg = ColorUtils.calculateLuminance(bgColor) < 0.4;
                int overrideColor = overrideColors[0];
                for (int c : overrideColors) {
                    if ((!darkBg && ColorUtils.calculateLuminance(c) < 0.75)
                            || (darkBg && ColorUtils.calculateLuminance(c) > 0.25)) {
                        overrideColor = c;
                        break;
                    }
                }
                if (overrideColor != 0)
                    CompoundButtonCompat.setButtonTintList(mCheckBox,
                            ColorStateList.valueOf(overrideColor));
                else
                    CompoundButtonCompat.setButtonTintList(mCheckBox, mDefaultButtonTintList);
            }

            @Override
            public void onClick(View v) {
                ThemeOptionSetting themeEntry = (ThemeOptionSetting) getEntry();
                if (getEntry().isChecked() && themeEntry.linkedCustomTheme != null) {
                    themeEntry.fragment.openThemeEditor(themeEntry.linkedCustomTheme);
                    return;
                }
                super.onClick(v);
            }

            @Override
            public boolean onLongClick(View v) {
                ThemeOptionSetting themeEntry = (ThemeOptionSetting) getEntry();
                MenuBottomSheetDialog menu = new MenuBottomSheetDialog(v.getContext());
                menu.addItem(R.string.action_copy, R.drawable.ic_content_copy,
                        (MenuBottomSheetDialog.Item i) -> {
                            ThemeInfo newTheme;
                            if (themeEntry.linkedCustomTheme != null)
                                newTheme = themeEntry.fragment.createNewTheme(
                                        themeEntry.linkedCustomTheme);
                            else
                                newTheme = themeEntry.fragment.createNewTheme(
                                        themeEntry.linkedBaseTheme);
                            ThemeManager.getInstance(null).setTheme(newTheme);
                            themeEntry.fragment.openThemeEditor(newTheme);
                            themeEntry.fragment.getActivity().recreate();
                            return true;
                        });
                if (themeEntry.linkedCustomTheme != null) {
                    menu.addItem(R.string.action_edit, R.drawable.ic_edit,
                            (MenuBottomSheetDialog.Item i) -> {
                                themeEntry.fragment.openThemeEditor(themeEntry.linkedCustomTheme);
                                return true;
                            });
                    menu.addItem(R.string.action_delete, R.drawable.ic_delete,
                            (MenuBottomSheetDialog.Item i) -> {
                                ThemeManager.getInstance(null)
                                        .deleteTheme(themeEntry.linkedCustomTheme);
                                getEntry().getOwner().remove(getEntry().getIndex());
                                themeEntry.fragment.getActivity().recreate();
                                return true;
                            });
                }
                menu.show();
                return true;
            }
        }
    }

    private static final class PresetOptionSetting extends RadioButtonSetting {

        private InterfaceSettingsFragment fragment;
        private AppearancePresetManager manager;
        private AppearancePreset linkedPreset;

        PresetOptionSetting(String name, Group group) {
            super(name, group);
        }

        PresetOptionSetting linkPreset(InterfaceSettingsFragment fragment,
                                       AppearancePresetManager manager,
                                       AppearancePreset preset) {
            this.fragment = fragment;
            this.manager = manager;
            setChecked(manager.getCurrentPreset() == preset);
            linkedPreset = preset;
            return this;
        }

        @Override
        public void setChecked(boolean checked) {
            boolean apply = checked && !isChecked() && linkedPreset != null
                    && linkedPreset.isApplicable() && fragment != null
                    && !fragment.mUpdatingPresetSelection;
            super.setChecked(checked);
            if (!apply)
                return;
            manager.applyPreset(linkedPreset);
            if (fragment.getActivity() != null)
                fragment.getActivity().recreate();
        }
    }

    private static final class UserPresetOptionSetting extends RadioButtonSetting {

        private static final int sHolder = SettingsListAdapter.registerViewHolder(Holder.class,
                R.layout.settings_list_checkbox_entry);

        private InterfaceSettingsFragment fragment;
        private ThemeManager manager;
        private ThemeInfo linkedTheme;

        UserPresetOptionSetting(String name, Group group) {
            super(name, group);
        }

        UserPresetOptionSetting linkPreset(InterfaceSettingsFragment fragment,
                                           ThemeManager manager, ThemeInfo theme) {
            this.fragment = fragment;
            this.manager = manager;
            setChecked(manager.getCurrentCustomTheme() == theme);
            linkedTheme = theme;
            return this;
        }

        @Override
        public int getViewHolder() {
            return sHolder;
        }

        @Override
        public void setChecked(boolean checked) {
            boolean apply = checked && !isChecked() && linkedTheme != null
                    && fragment != null && !fragment.mUpdatingPresetSelection;
            super.setChecked(checked);
            if (!apply)
                return;
            manager.setTheme(linkedTheme);
            if (fragment.getActivity() != null)
                fragment.getActivity().recreate();
        }

        public static class Holder extends RadioButtonSetting.Holder
                implements View.OnLongClickListener {

            Holder(View itemView, SettingsListAdapter adapter) {
                super(itemView, adapter);
                itemView.setOnLongClickListener(this);
            }

            @Override
            public boolean onLongClick(View v) {
                UserPresetOptionSetting entry = (UserPresetOptionSetting) getEntry();
                ThemeInfo theme = entry.linkedTheme;
                if (theme == null || entry.fragment == null)
                    return false;
                String name = theme.name == null ? "" : theme.name;
                new AlertDialog.Builder(v.getContext())
                        .setTitle(R.string.action_delete)
                        .setMessage(name)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                            UserPresetStore.unmark(v.getContext(), theme);
                            entry.manager.deleteTheme(theme);
                            if (entry.fragment.getActivity() != null)
                                entry.fragment.getActivity().recreate();
                        })
                        .show();
                return true;
            }
        }
    }
}

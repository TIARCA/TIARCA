package io.mrarm.irc.setting.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import io.mrarm.irc.util.DefaultPreferences;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.CompoundButtonCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

import io.mrarm.chatlib.dto.MessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.irc.MessageFormatSettingsActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.SettingsActivity;
import io.mrarm.irc.ThemeEditorActivity;
import io.mrarm.irc.ThemedActivity;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.ChatSettings;
import io.mrarm.irc.config.SettingsHelper;
import io.mrarm.irc.dialog.MenuBottomSheetDialog;
import io.mrarm.irc.setting.CheckBoxSetting;
import io.mrarm.irc.setting.ClickableSetting;
import io.mrarm.irc.setting.FontSizeSetting;
import io.mrarm.irc.setting.ListSetting;
import io.mrarm.irc.setting.ListWithCustomSetting;
import io.mrarm.irc.setting.RadioButtonSetting;
import io.mrarm.irc.setting.SettingsHeader;
import io.mrarm.irc.setting.SettingsListAdapter;
import io.mrarm.irc.util.EntryRecyclerViewAdapter;
import io.mrarm.irc.util.AppLocaleManager;
import io.mrarm.irc.util.MessageBuilder;
import io.mrarm.irc.util.StyledAttributesHelper;
import io.mrarm.irc.util.theme.ThemeInfo;
import io.mrarm.irc.util.theme.ThemeManager;

public class InterfaceSettingsFragment extends SettingsListFragment
        implements NamedSettingsFragment {

    private ClickableSetting mMessageFormatItem;
    private MessageInfo mSampleMessage;
    private ActivityResultLauncher<Intent> mThemeEditorLauncher;
    private ActivityResultLauncher<Intent> mImportThemeLauncher;

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
                getResources().getStringArray(R.array.pref_entries_language),
                getResources().getStringArray(R.array.pref_entry_values_language),
                AppLocaleManager.getLanguage(prefs))
                .linkPreference(prefs, AppLocaleManager.PREF_APP_LANGUAGE)
                .addListener((EntryRecyclerViewAdapter.Entry entry) ->
                        AppLocaleManager.applyLanguage(
                                ((ListSetting) entry).getSelectedOptionValue())));
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
        a.add(new CheckBoxSetting(getString(R.string.pref_title_autocorrect),
                getString(R.string.pref_summary_autocorrect))
                .linkSetting(prefs, ChatSettings.PREF_TEXT_AUTOCORRECT_ENABLED));
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

        a.add(new SettingsHeader(getString(R.string.pref_header_misc)));
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
        if (((ThemedActivity) getActivity()).hasThemeChanged()) {
            getActivity().recreate();
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
        try {
            Uri uri = data.getData();
            try (ParcelFileDescriptor desc = requireActivity().getContentResolver()
                    .openFileDescriptor(uri, "r")) {
                if (desc == null)
                    throw new IOException("Unable to open theme");
                try (BufferedReader re = new BufferedReader(new FileReader(desc.getFileDescriptor()))) {
                ThemeManager.getInstance(getContext()).importTheme(re);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
        recreateAdapter();
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
                // Recreate only after the new preference has been stored. The old listener
                // recreated the Activity while the radio group was still changing, before the
                // selected theme could be applied reliably.
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

}

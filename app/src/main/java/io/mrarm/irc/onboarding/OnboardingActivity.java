package io.mrarm.irc.onboarding;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.mrarm.chatlib.dto.ChannelList;
import io.mrarm.irc.ChannelListActivity;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.NetworkCatalogActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.TemporaryChannelListConnection;
import io.mrarm.irc.ThemedActivity;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.IdentitySettings;
import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;
import io.mrarm.irc.util.DefaultPreferences;
import io.mrarm.irc.util.SimpleTextWatcher;
import io.mrarm.irc.util.theme.AppearancePreset;
import io.mrarm.irc.util.theme.AppearancePresetManager;
import io.mrarm.irc.util.theme.ThemeInfo;
import io.mrarm.irc.util.theme.ThemeManager;
import io.mrarm.irc.util.theme.UserPresetStore;
import io.mrarm.irc.view.ChipsEditText;
import io.mrarm.irc.view.OnboardingPresetPreviewView;

/** Four-step first-run wizard. Existing configuration is updated, never wholesale deleted. */
public class OnboardingActivity extends ThemedActivity {

    public static final String EXTRA_MANUAL_RESET = "manual_reset";

    private static final String TAG = "OnboardingActivity";
    private static final int STEP_WELCOME = 0;
    private static final int STEP_IDENTITY = 1;
    private static final int STEP_APPEARANCE = 2;
    private static final int STEP_NETWORK = 3;
    private static final int STEP_COUNT = 4;

    private static final String STATE_STEP = "step";
    private static final String STATE_IMPORTED_PRESET = "imported_preset";
    private static final String STATE_OTHER_NETWORK = "other_network";
    private static final String STATE_MANUAL_NETWORK = "manual_network";
    private static final String STATE_NETWORK_NAME = "network_name";
    private static final String STATE_NETWORK_ADDRESSES = "network_addresses";
    private static final String STATE_NETWORK_PORT = "network_port";
    private static final String STATE_NETWORK_TLS = "network_tls";
    private static final String STATE_SASL_USER_TOUCHED = "sasl_user_touched";
    private static final String STATE_CHANNELS = "channels";

    private int mStep;
    private boolean mManualReset;
    private boolean mUseOtherNetwork;
    private boolean mManualNetwork;
    private String mSelectedNetworkName;
    private ArrayList<String> mSelectedNetworkAddresses;
    private int mSelectedNetworkPort = 6697;
    private boolean mSelectedNetworkTls = true;
    private String mImportedPresetName;
    private boolean mSyncingSaslUser;
    private boolean mSaslUserTouched;

    private View[] mStepViews;
    private TextView mStepLabel;
    private EditText mNickname;
    private EditText mIdent;
    private EditText mRealname;
    private CheckBox mApplyExisting;
    private RadioGroup mPresetGroup;
    private OnboardingPresetPreviewView mPresetPreview;
    private TextView mImportedPreset;
    private RadioButton mSimosnap;
    private TextView mSelectedNetwork;
    private LinearLayout mManualServerFields;
    private EditText mNetworkName;
    private EditText mServerAddress;
    private EditText mServerPort;
    private CheckBox mServerTls;
    private ChipsEditText mChannels;
    private CheckBox mSaslCheck;
    private LinearLayout mSaslFields;
    private EditText mSaslUser;
    private EditText mSaslPassword;
    private Button mBack;
    private Button mNext;

    private TemporaryChannelListConnection mTemporaryChannelListConnection;
    private AlertDialog mChannelListProgressDialog;

    private ActivityResultLauncher<Intent> mPresetLauncher;
    private ActivityResultLauncher<Intent> mNetworkLauncher;
    private ActivityResultLauncher<Intent> mChannelListPickerLauncher;

    public static Intent createIntent(Context context, boolean manualReset) {
        return new Intent(context, OnboardingActivity.class)
                .putExtra(EXTRA_MANUAL_RESET, manualReset);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mManualReset = getIntent().getBooleanExtra(EXTRA_MANUAL_RESET, false);
        registerLaunchers();
        setContentView(R.layout.activity_onboarding);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(R.string.onboarding_title);

        bindViews();
        setupPresetChoices();
        setupListeners();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            mStep = STEP_WELCOME;
            mSimosnap.setChecked(true);
            if (!mManualReset) {
                String[] defaultNicks = AppSettings.getDefaultNicks();
                if (defaultNicks != null && defaultNicks.length > 0)
                    mNickname.setText(defaultNicks[0]);
            }
        }

        boolean showExisting = mManualReset &&
                !ServerConfigManager.getInstance(this).getServers().isEmpty();
        mApplyExisting.setVisibility(showExisting ? View.VISIBLE : View.GONE);
        updateImportedPresetLabel();
        showStep();
    }

    private void registerLaunchers() {
        mPresetLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
                        importPreset(result.getData().getData());
                });
        mNetworkLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() != Activity.RESULT_OK || data == null)
                        return;
                    boolean manual = data.getBooleanExtra(NetworkCatalogActivity.RESULT_MANUAL, false);
                    mUseOtherNetwork = true;
                    mManualNetwork = manual;
                    mSimosnap.setChecked(false);
                    clearSelectedChannels();
                    if (manual) {
                        mSelectedNetworkName = null;
                        mSelectedNetworkAddresses = null;
                        mManualServerFields.setVisibility(View.VISIBLE);
                        mSelectedNetwork.setText(R.string.onboarding_manual_network);
                        mSelectedNetwork.setVisibility(View.VISIBLE);
                    } else {
                        mSelectedNetworkName = data.getStringExtra(NetworkCatalogActivity.RESULT_NAME);
                        mSelectedNetworkAddresses = data.getStringArrayListExtra(
                                NetworkCatalogActivity.RESULT_ADDRESSES);
                        mSelectedNetworkPort = data.getIntExtra(
                                NetworkCatalogActivity.RESULT_PORT, 6697);
                        mSelectedNetworkTls = data.getBooleanExtra(
                                NetworkCatalogActivity.RESULT_TLS, true);
                        mManualServerFields.setVisibility(View.GONE);
                        mSelectedNetwork.setText(getString(R.string.onboarding_selected_network,
                                mSelectedNetworkName));
                        mSelectedNetwork.setVisibility(View.VISIBLE);
                    }
                });
        mChannelListPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
                        return;
                    ArrayList<String> selected = result.getData().getStringArrayListExtra(
                            ChannelListActivity.RESULT_SELECTED_CHANNELS);
                    if (selected != null)
                        mergeSelectedChannels(selected);
                });
    }

    private void bindViews() {
        mStepViews = new View[] {
                findViewById(R.id.onboarding_step_welcome),
                findViewById(R.id.onboarding_step_identity),
                findViewById(R.id.onboarding_step_appearance),
                findViewById(R.id.onboarding_step_network)
        };
        mStepLabel = findViewById(R.id.onboarding_step_label);
        mNickname = findViewById(R.id.onboarding_nickname);
        mIdent = findViewById(R.id.onboarding_ident);
        mRealname = findViewById(R.id.onboarding_realname);
        mApplyExisting = findViewById(R.id.onboarding_apply_existing);
        mPresetGroup = findViewById(R.id.onboarding_preset_group);
        mPresetPreview = findViewById(R.id.onboarding_preset_preview);
        mImportedPreset = findViewById(R.id.onboarding_imported_preset);
        mSimosnap = findViewById(R.id.onboarding_network_simosnap);
        mSelectedNetwork = findViewById(R.id.onboarding_selected_network);
        mManualServerFields = findViewById(R.id.onboarding_manual_server_fields);
        mNetworkName = findViewById(R.id.onboarding_network_name);
        mServerAddress = findViewById(R.id.onboarding_server_address);
        mServerPort = findViewById(R.id.onboarding_server_port);
        mServerTls = findViewById(R.id.onboarding_server_tls);
        mChannels = findViewById(R.id.onboarding_channels);
        mSaslCheck = findViewById(R.id.onboarding_sasl_check);
        mSaslFields = findViewById(R.id.onboarding_sasl_fields);
        mSaslUser = findViewById(R.id.onboarding_sasl_user);
        mSaslPassword = findViewById(R.id.onboarding_sasl_password);
        mBack = findViewById(R.id.onboarding_back);
        mNext = findViewById(R.id.onboarding_next);
    }

    private void setupListeners() {
        mBack.setOnClickListener(view -> {
            if (mStep == STEP_WELCOME) {
                finish();
                return;
            }
            mStep--;
            showStep();
        });
        mNext.setOnClickListener(view -> {
            if (mStep == STEP_IDENTITY) {
                continueWithNickname(() -> {
                    mStep++;
                    showStep();
                });
                return;
            }
            if (mStep < STEP_NETWORK) {
                mStep++;
                showStep();
                return;
            }
            commitAndEnter();
        });

        findViewById(R.id.onboarding_import_preset).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            mPresetLauncher.launch(intent);
        });

        mSimosnap.setOnClickListener(view -> {
            mUseOtherNetwork = false;
            mManualNetwork = false;
            mSelectedNetwork.setVisibility(View.GONE);
            mManualServerFields.setVisibility(View.GONE);
            clearSelectedChannels();
        });
        findViewById(R.id.onboarding_choose_other_network).setOnClickListener(view -> {
            Intent intent = new Intent(this, NetworkCatalogActivity.class)
                    .putExtra(NetworkCatalogActivity.ARG_PICK_ONLY, true);
            mNetworkLauncher.launch(intent);
        });
        findViewById(R.id.onboarding_channel_list).setOnClickListener(
                view -> startTemporaryChannelList());

        mSaslCheck.setOnCheckedChangeListener((button, checked) -> {
            mSaslFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && TextUtils.isEmpty(mSaslUser.getText()))
                mSaslUserTouched = false;
            if (checked && !mSaslUserTouched)
                syncSaslUserFromNickname();
        });
        mNickname.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (!mSaslUserTouched)
                syncSaslUserFromNickname();
        }));
        mSaslUser.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (!mSyncingSaslUser && mSaslUser.hasFocus())
                mSaslUserTouched = true;
        }));
    }

    private void setupPresetChoices() {
        bindPreset(R.id.onboarding_preset_graphic_light, AppearancePreset.GRAPHIC_LIGHT);
        bindPreset(R.id.onboarding_preset_graphic_dark, AppearancePreset.GRAPHIC_DARK);
        bindPreset(R.id.onboarding_preset_irc_light, AppearancePreset.IRC_LIGHT);
        bindPreset(R.id.onboarding_preset_irc_dark, AppearancePreset.IRC_DARK);
        bindPreset(R.id.onboarding_preset_terminal, AppearancePreset.TERMINAL);
        bindPreset(R.id.onboarding_preset_color_blind, AppearancePreset.COLOR_BLIND);

        AppearancePreset current = AppearancePresetManager.getInstance(this).getCurrentPreset();
        int checkedId = presetViewId(current);
        if (checkedId != View.NO_ID)
            mPresetGroup.check(checkedId);
        updatePresetPreview(current);
    }

    private void bindPreset(int viewId, AppearancePreset preset) {
        findViewById(viewId).setOnClickListener(view -> {
            mImportedPresetName = null;
            updateImportedPresetLabel();
            AppearancePresetManager.getInstance(this).applyPreset(preset);
            recreate();
        });
    }

    private int presetViewId(AppearancePreset preset) {
        switch (preset) {
            case GRAPHIC_LIGHT: return R.id.onboarding_preset_graphic_light;
            case GRAPHIC_DARK: return R.id.onboarding_preset_graphic_dark;
            case IRC_LIGHT: return R.id.onboarding_preset_irc_light;
            case IRC_DARK: return R.id.onboarding_preset_irc_dark;
            case TERMINAL: return R.id.onboarding_preset_terminal;
            case COLOR_BLIND: return R.id.onboarding_preset_color_blind;
            default: return View.NO_ID;
        }
    }

    private void updatePresetPreview(AppearancePreset preset) {
        if (mPresetPreview == null || mImportedPresetName != null) {
            if (mPresetPreview != null)
                mPresetPreview.setVisibility(View.GONE);
            return;
        }
        if (presetViewId(preset) == View.NO_ID) {
            mPresetPreview.setVisibility(View.GONE);
            return;
        }
        mPresetPreview.renderPreview();
        mPresetPreview.setVisibility(View.VISIBLE);
    }

    private void syncSaslUserFromNickname() {
        if (mSaslUserTouched)
            return;
        String nick = mNickname.getText().toString().trim();
        if (nick.isEmpty())
            return;
        mSyncingSaslUser = true;
        mSaslUser.setText(nick);
        mSaslUser.setSelection(mSaslUser.length());
        mSyncingSaslUser = false;
    }

    private void continueWithNickname(Runnable continuation) {
        String nick = mNickname.getText().toString().trim();
        if (!nick.isEmpty()) {
            continuation.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.onboarding_nickname_missing_title)
                .setMessage(R.string.onboarding_nickname_missing_body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.onboarding_continue, (dialog, which) -> {
                    String generated = IdentitySettings.createAutomaticIdentity();
                    mNickname.setText(generated);
                    mNickname.setSelection(mNickname.length());
                    syncSaslUserFromNickname();
                    continuation.run();
                })
                .show();
    }

    private boolean validateNickname() {
        if (!TextUtils.isEmpty(mNickname.getText().toString().trim()))
            return true;
        mNickname.setError(getString(R.string.onboarding_error_nickname));
        mNickname.requestFocus();
        return false;
    }

    private boolean validateNetwork() {
        if (!mUseOtherNetwork)
            return true;
        if (!mManualNetwork)
            return mSelectedNetworkName != null && mSelectedNetworkAddresses != null
                    && !mSelectedNetworkAddresses.isEmpty();
        String name = mNetworkName.getText().toString().trim();
        String address = mServerAddress.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(mServerPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (!name.isEmpty() && !address.isEmpty() && port > 0 && port <= 65535)
            return true;
        Toast.makeText(this, R.string.onboarding_error_server, Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean validateAuth() {
        if (!mSaslCheck.isChecked())
            return true;
        if (!TextUtils.isEmpty(mSaslUser.getText()) && !TextUtils.isEmpty(mSaslPassword.getText()))
            return true;
        Toast.makeText(this, R.string.onboarding_error_auth, Toast.LENGTH_LONG).show();
        return false;
    }

    private void showStep() {
        for (int i = 0; i < mStepViews.length; i++)
            mStepViews[i].setVisibility(i == mStep ? View.VISIBLE : View.GONE);
        mStepLabel.setText(getString(R.string.onboarding_step, mStep + 1, STEP_COUNT));
        mBack.setVisibility(mStep == STEP_WELCOME ? View.INVISIBLE : View.VISIBLE);
        mNext.setText(mStep == STEP_NETWORK ? R.string.onboarding_enter : R.string.onboarding_next);
        if (mStep == STEP_NETWORK && !mSaslUserTouched)
            syncSaslUserFromNickname();
        View scroll = findViewById(R.id.onboarding_scroll);
        if (scroll != null)
            scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void importPreset(Uri uri) {
        if (uri == null)
            return;
        ThemeManager manager = ThemeManager.getInstance(this);
        Set<UUID> before = UserPresetStore.snapshotThemeIds(manager);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null)
                throw new IOException("Unable to open preset");
            manager.importTheme(input);
            ThemeInfo imported = UserPresetStore.findImportedTheme(manager, before);
            String displayName = getDisplayName(uri);
            if (imported != null) {
                String fileName = UserPresetStore.nameFromFile(displayName);
                boolean useFileName = UserPresetStore.isLegacyThemeFile(displayName)
                        || imported.name == null || imported.name.trim().isEmpty();
                if (useFileName && fileName != null) {
                    imported.name = fileName;
                    manager.saveTheme(imported);
                }
                UserPresetStore.mark(this, imported);
                mImportedPresetName = imported.name != null ? imported.name : fileName;
            } else {
                mImportedPresetName = displayName;
            }
            mPresetGroup.clearCheck();
            recreate();
        } catch (IOException | RuntimeException e) {
            Toast.makeText(this, R.string.onboarding_import_error, Toast.LENGTH_LONG).show();
        }
    }

    private String getDisplayName(Uri uri) {
        if (uri == null)
            return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0)
                        return cursor.getString(index);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return uri.getLastPathSegment();
    }

    private void updateImportedPresetLabel() {
        if (mImportedPresetName == null || mImportedPresetName.trim().isEmpty()) {
            mImportedPreset.setVisibility(View.GONE);
            updatePresetPreview(AppearancePresetManager.getInstance(this).getCurrentPreset());
            return;
        }
        mImportedPreset.setText(getString(R.string.onboarding_imported_preset, mImportedPresetName));
        mImportedPreset.setVisibility(View.VISIBLE);
        mPresetGroup.clearCheck();
        mPresetPreview.setVisibility(View.GONE);
    }

    private void startTemporaryChannelList() {
        if (!validateNickname() || !validateNetwork() || !validateAuth())
            return;
        if (mTemporaryChannelListConnection != null)
            mTemporaryChannelListConnection.cancel();

        ServerConfigData temporary = buildTemporaryServerConfig();
        if (temporary == null)
            return;

        ProgressBar progress = new ProgressBar(this);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        progress.setPadding(padding, padding, padding, padding);
        mChannelListProgressDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.server_channel_list_connecting)
                .setView(progress)
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> cancelChannelList())
                .setOnCancelListener(dialog -> cancelChannelList())
                .show();

        mTemporaryChannelListConnection = new TemporaryChannelListConnection(this, temporary);
        mTemporaryChannelListConnection.start(new TemporaryChannelListConnection.Callback() {
            @Override
            public void onSuccess(List<ChannelList.Entry> entries) {
                runOnUiThread(() -> {
                    mTemporaryChannelListConnection = null;
                    dismissChannelListProgress();
                    if (isFinishing() || isDestroyed())
                        return;
                    if (entries == null || entries.isEmpty()) {
                        Toast.makeText(OnboardingActivity.this,
                                R.string.server_channel_list_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    mChannelListPickerLauncher.launch(
                            ChannelListActivity.getPickerIntent(OnboardingActivity.this, entries));
                });
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Temporary onboarding LIST connection failed", error);
                runOnUiThread(() -> {
                    mTemporaryChannelListConnection = null;
                    dismissChannelListProgress();
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(OnboardingActivity.this,
                                R.string.server_channel_list_connection_error,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private ServerConfigData buildTemporaryServerConfig() {
        SelectedNetwork selection = getSelectedNetwork();
        if (selection == null)
            return null;
        ServerConfigData data = new ServerConfigData();
        data.uuid = UUID.randomUUID();
        data.name = selection.name;
        data.setConnectionAddresses(selection.addresses);
        data.port = selection.port;
        data.ssl = selection.tls;
        data.charset = "UTF-8";
        data.nicks = Arrays.asList(mNickname.getText().toString().trim());
        String ident = mIdent.getText().toString().trim();
        data.user = ident.isEmpty() ? null : ident;
        String realname = mRealname.getText().toString().trim();
        data.realname = realname.isEmpty() ? null : realname;
        if (mSaslCheck.isChecked()) {
            data.authMode = ServerConfigData.AUTH_SASL;
            data.authUser = mSaslUser.getText().toString().trim();
            data.authPass = mSaslPassword.getText().toString();
        }
        return data;
    }

    private void cancelChannelList() {
        if (mTemporaryChannelListConnection != null)
            mTemporaryChannelListConnection.cancel();
        mTemporaryChannelListConnection = null;
    }

    private void dismissChannelListProgress() {
        if (mChannelListProgressDialog != null) {
            mChannelListProgressDialog.dismiss();
            mChannelListProgressDialog = null;
        }
    }

    private void mergeSelectedChannels(List<String> selected) {
        ArrayList<String> merged = new ArrayList<>();
        for (String existing : mChannels.getItems())
            addChannelIfMissing(merged, existing);
        for (String channel : selected)
            addChannelIfMissing(merged, channel);
        mChannels.setItems(merged);
    }

    private void clearSelectedChannels() {
        mChannels.setItems(new ArrayList<>());
    }

    private static void addChannelIfMissing(List<String> channels, String candidate) {
        if (candidate == null)
            return;
        String value = candidate.trim();
        if (value.isEmpty() || value.equals("#"))
            return;
        for (String existing : channels) {
            if (existing.equalsIgnoreCase(value))
                return;
        }
        channels.add(value);
    }

    private ArrayList<String> getSelectedChannels() {
        ArrayList<String> result = new ArrayList<>();
        for (String channel : mChannels.getItems())
            addChannelIfMissing(result, channel);
        return result;
    }

    private void commitAndEnter() {
        if (!validateNickname() || !validateNetwork() || !validateAuth())
            return;

        String nick = mNickname.getText().toString().trim();
        String ident = mIdent.getText().toString().trim();
        String realname = mRealname.getText().toString().trim();
        persistGlobalIdentity(nick, ident, realname);

        ServerConfigManager configManager = ServerConfigManager.getInstance(this);
        ServerConnectionManager connectionManager = ServerConnectionManager.getInstance(this);
        ServerConfigData target = resolveTargetServer(configManager);
        if (target == null) {
            Toast.makeText(this, R.string.server_save_error, Toast.LENGTH_LONG).show();
            return;
        }

        LinkedHashMap<UUID, ServerConfigData> affected = new LinkedHashMap<>();
        if (mManualReset && mApplyExisting.isChecked()) {
            for (ServerConfigData server : configManager.getServers())
                affected.put(server.uuid, server);
        }
        if (target.uuid != null && configManager.findServer(target.uuid) != null)
            affected.put(target.uuid, target);

        Set<UUID> reconnect = new HashSet<>();
        for (ServerConfigData server : affected.values()) {
            ServerConnectionInfo connection = connectionManager.getConnection(server.uuid);
            if (connection != null) {
                reconnect.add(server.uuid);
                connection.disconnect();
                connectionManager.removeConnection(connection);
            }
        }

        try {
            if (mManualReset && mApplyExisting.isChecked()) {
                for (ServerConfigData server : configManager.getServers()) {
                    applyIdentity(server, nick, ident, realname);
                    configManager.saveServer(server);
                }
            }

            applyIdentity(target, nick, ident, realname);
            if (mSaslCheck.isChecked()) {
                target.authMode = ServerConfigData.AUTH_SASL;
                target.authUser = mSaslUser.getText().toString().trim();
                target.authPass = mSaslPassword.getText().toString();
                target.authCertData = null;
                target.authCertPrivKey = null;
                target.authCertPrivKeyType = null;
            }
            mergeAutojoinChannels(target, getSelectedChannels());
            configManager.saveServer(target);
        } catch (IOException e) {
            Toast.makeText(this, R.string.server_save_error, Toast.LENGTH_LONG).show();
            return;
        }

        OnboardingState.markCompleted(this);

        for (UUID uuid : reconnect) {
            ServerConfigData server = configManager.findServer(uuid);
            if (server != null)
                connectionManager.tryCreateConnection(server, this);
        }
        if (!connectionManager.hasConnection(target.uuid))
            connectionManager.tryCreateConnection(target, this);

        Intent main = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
    }

    private void persistGlobalIdentity(String nick, String ident, String realname) {
        SharedPreferences.Editor editor = DefaultPreferences.get(this).edit()
                .putString(AppSettings.PREF_DEFAULT_NICKS, nick);
        if (ident.isEmpty()) {
            editor.remove(AppSettings.PREF_DEFAULT_USER)
                    .putBoolean(IdentitySettings.PREF_CUSTOM_USERNAME_ENABLED, false);
        } else {
            editor.putString(AppSettings.PREF_DEFAULT_USER, ident)
                    .putBoolean(IdentitySettings.PREF_CUSTOM_USERNAME_ENABLED, true);
        }
        if (realname.isEmpty())
            editor.remove(AppSettings.PREF_DEFAULT_REALNAME);
        else
            editor.putString(AppSettings.PREF_DEFAULT_REALNAME, realname);
        editor.apply();
    }

    private ServerConfigData resolveTargetServer(ServerConfigManager manager) {
        SelectedNetwork selection = getSelectedNetwork();
        if (selection == null)
            return null;

        OnboardingNetworkResolver.Resolution resolution = OnboardingNetworkResolver.resolve(
                manager.getServers(), selection.name, selection.addresses);
        ServerConfigData server = resolution.existing;
        if (server == null) {
            server = new ServerConfigData();
            server.uuid = UUID.randomUUID();
            server.rejoinChannels = true;
            server.hideJoinPartMessages = true;
            server.charset = "UTF-8";
        }
        server.name = resolution.name;
        server.setConnectionAddresses(selection.addresses);
        server.port = selection.port;
        server.ssl = selection.tls;
        return server;
    }

    private SelectedNetwork getSelectedNetwork() {
        if (!mUseOtherNetwork) {
            return new SelectedNetwork("Simosnap",
                    new ArrayList<>(Arrays.asList("irc.simosnap.org")), 6697, true);
        }
        if (!mManualNetwork) {
            if (mSelectedNetworkName == null || mSelectedNetworkAddresses == null ||
                    mSelectedNetworkAddresses.isEmpty())
                return null;
            return new SelectedNetwork(mSelectedNetworkName,
                    new ArrayList<>(mSelectedNetworkAddresses), mSelectedNetworkPort,
                    mSelectedNetworkTls);
        }
        String name = mNetworkName.getText().toString().trim();
        String address = mServerAddress.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(mServerPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
        ArrayList<String> addresses = new ArrayList<>();
        addresses.add(address);
        return new SelectedNetwork(name, addresses, port, mServerTls.isChecked());
    }

    private static void mergeAutojoinChannels(ServerConfigData target, List<String> selected) {
        if (selected == null || selected.isEmpty())
            return;
        ArrayList<String> merged = new ArrayList<>();
        if (target.autojoinChannels != null) {
            for (String channel : target.autojoinChannels)
                addChannelIfMissing(merged, channel);
        }
        for (String channel : selected)
            addChannelIfMissing(merged, channel);
        target.autojoinChannels = merged;
    }

    private static void applyIdentity(ServerConfigData server, String nick, String ident,
                                      String realname) {
        server.nicks = Arrays.asList(nick);
        server.user = ident.isEmpty() ? null : ident;
        server.realname = realname.isEmpty() ? null : realname;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStep);
        outState.putString(STATE_IMPORTED_PRESET, mImportedPresetName);
        outState.putBoolean(STATE_OTHER_NETWORK, mUseOtherNetwork);
        outState.putBoolean(STATE_MANUAL_NETWORK, mManualNetwork);
        outState.putString(STATE_NETWORK_NAME, mSelectedNetworkName);
        outState.putStringArrayList(STATE_NETWORK_ADDRESSES, mSelectedNetworkAddresses);
        outState.putInt(STATE_NETWORK_PORT, mSelectedNetworkPort);
        outState.putBoolean(STATE_NETWORK_TLS, mSelectedNetworkTls);
        outState.putBoolean(STATE_SASL_USER_TOUCHED, mSaslUserTouched);
        outState.putStringArrayList(STATE_CHANNELS, getSelectedChannels());
    }

    private void restoreState(Bundle state) {
        mStep = state.getInt(STATE_STEP, STEP_WELCOME);
        mImportedPresetName = state.getString(STATE_IMPORTED_PRESET);
        mUseOtherNetwork = state.getBoolean(STATE_OTHER_NETWORK, false);
        mManualNetwork = state.getBoolean(STATE_MANUAL_NETWORK, false);
        mSelectedNetworkName = state.getString(STATE_NETWORK_NAME);
        mSelectedNetworkAddresses = state.getStringArrayList(STATE_NETWORK_ADDRESSES);
        mSelectedNetworkPort = state.getInt(STATE_NETWORK_PORT, 6697);
        mSelectedNetworkTls = state.getBoolean(STATE_NETWORK_TLS, true);
        mSaslUserTouched = state.getBoolean(STATE_SASL_USER_TOUCHED, false);
        ArrayList<String> channels = state.getStringArrayList(STATE_CHANNELS);
        if (channels != null)
            mChannels.setItems(channels);
        mSimosnap.setChecked(!mUseOtherNetwork);
        mManualServerFields.setVisibility(mUseOtherNetwork && mManualNetwork
                ? View.VISIBLE : View.GONE);
        if (mUseOtherNetwork) {
            mSelectedNetwork.setText(mManualNetwork
                    ? getString(R.string.onboarding_manual_network)
                    : getString(R.string.onboarding_selected_network, mSelectedNetworkName));
            mSelectedNetwork.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        cancelChannelList();
        dismissChannelListProgress();
        super.onDestroy();
    }

    private static final class SelectedNetwork {
        final String name;
        final ArrayList<String> addresses;
        final int port;
        final boolean tls;

        SelectedNetwork(String name, ArrayList<String> addresses, int port, boolean tls) {
            this.name = name;
            this.addresses = addresses;
            this.port = port;
            this.tls = tls;
        }
    }
}

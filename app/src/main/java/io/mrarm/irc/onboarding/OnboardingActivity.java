package io.mrarm.irc.onboarding;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.NetworkCatalogActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.ServerConnectionManager;
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

/** Four-step first-run wizard. Existing configuration is updated, never wholesale deleted. */
public class OnboardingActivity extends ThemedActivity {

    public static final String EXTRA_MANUAL_RESET = "manual_reset";

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
    private TextView mImportedPreset;
    private RadioButton mSimosnap;
    private TextView mSelectedNetwork;
    private LinearLayout mManualServerFields;
    private EditText mNetworkName;
    private EditText mServerAddress;
    private EditText mServerPort;
    private CheckBox mServerTls;
    private CheckBox mSaslCheck;
    private LinearLayout mSaslFields;
    private EditText mSaslUser;
    private EditText mSaslPassword;
    private Button mBack;
    private Button mNext;

    private ActivityResultLauncher<Intent> mPresetLauncher;
    private ActivityResultLauncher<Intent> mNetworkLauncher;

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
        mImportedPreset = findViewById(R.id.onboarding_imported_preset);
        mSimosnap = findViewById(R.id.onboarding_network_simosnap);
        mSelectedNetwork = findViewById(R.id.onboarding_selected_network);
        mManualServerFields = findViewById(R.id.onboarding_manual_server_fields);
        mNetworkName = findViewById(R.id.onboarding_network_name);
        mServerAddress = findViewById(R.id.onboarding_server_address);
        mServerPort = findViewById(R.id.onboarding_server_port);
        mServerTls = findViewById(R.id.onboarding_server_tls);
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
            if (mStep == STEP_IDENTITY && !validateNickname())
                return;
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
        });
        findViewById(R.id.onboarding_choose_other_network).setOnClickListener(view -> {
            Intent intent = new Intent(this, NetworkCatalogActivity.class)
                    .putExtra(NetworkCatalogActivity.ARG_PICK_ONLY, true);
            mNetworkLauncher.launch(intent);
        });

        mSaslCheck.setOnCheckedChangeListener((button, checked) -> {
            mSaslFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && !mSaslUserTouched)
                syncSaslUserFromNickname();
        });
        mNickname.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (mSaslCheck.isChecked() && !mSaslUserTouched)
                syncSaslUserFromNickname();
        }));
        mSaslUser.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (!mSyncingSaslUser)
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

    private void syncSaslUserFromNickname() {
        String nick = mNickname.getText().toString().trim();
        if (nick.isEmpty())
            return;
        mSyncingSaslUser = true;
        mSaslUser.setText(nick);
        mSaslUser.setSelection(mSaslUser.length());
        mSyncingSaslUser = false;
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
            return;
        }
        mImportedPreset.setText(getString(R.string.onboarding_imported_preset, mImportedPresetName));
        mImportedPreset.setVisibility(View.VISIBLE);
        mPresetGroup.clearCheck();
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
        if (!mUseOtherNetwork) {
            ServerConfigData simosnap = manager.findServer(OnboardingState.SIMOSNAP_UUID);
            if (simosnap != null)
                return simosnap;
            simosnap = new ServerConfigData();
            simosnap.uuid = OnboardingState.SIMOSNAP_UUID;
            simosnap.name = "Simosnap";
            simosnap.setConnectionAddresses(Arrays.asList("irc.simosnap.org"));
            simosnap.port = 6697;
            simosnap.ssl = true;
            simosnap.charset = "UTF-8";
            simosnap.rejoinChannels = true;
            simosnap.hideJoinPartMessages = true;
            return simosnap;
        }

        String name;
        ArrayList<String> addresses;
        int port;
        boolean tls;
        if (mManualNetwork) {
            name = mNetworkName.getText().toString().trim();
            addresses = new ArrayList<>();
            addresses.add(mServerAddress.getText().toString().trim());
            port = Integer.parseInt(mServerPort.getText().toString().trim());
            tls = mServerTls.isChecked();
        } else {
            name = mSelectedNetworkName;
            addresses = mSelectedNetworkAddresses;
            port = mSelectedNetworkPort;
            tls = mSelectedNetworkTls;
        }

        ServerConfigData server = findServerByName(manager.getServers(), name);
        if (server == null) {
            server = new ServerConfigData();
            server.uuid = UUID.randomUUID();
            server.rejoinChannels = true;
            server.hideJoinPartMessages = true;
            server.charset = "UTF-8";
        }
        server.name = name;
        server.setConnectionAddresses(addresses);
        server.port = port;
        server.ssl = tls;
        return server;
    }

    private static ServerConfigData findServerByName(List<ServerConfigData> servers, String name) {
        if (name == null)
            return null;
        for (ServerConfigData server : servers) {
            if (server.name != null && server.name.equalsIgnoreCase(name))
                return server;
        }
        return null;
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
}

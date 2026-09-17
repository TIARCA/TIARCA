package io.mrarm.irc.config;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mrarm.irc.ServerConnectionManager;
import io.mrarm.irc.util.DiagnosticLog;

public class ServerConfigManager {

    private static ServerConfigManager mInstance;

    private static final String TAG = "ServerConfigManager";

    private static final String SERVERS_PATH = "servers";
    private static final String SERVER_FILE_PREFIX = "server-";
    private static final String SERVER_FILE_SUFFIX = ".json";
    private static final String SERVER_ORDER_FILENAME = "server_order.json";
    private static final String SERVER_CERTS_FILE_PREFIX = "server-certs-";
    private static final String SERVER_CERTS_FILE_SUFFIX = ".jks";
    private static final String SERVER_LOGS_PATH = "chat-logs";
    private static final String SERVER_MISC_DATA_FILENAME = "misc-data.db";
    private static final String BUNDLED_SERVERS_PREFS = "bundled_servers";
    private static final String BUNDLED_SERVERS_INITIALIZED = "initialized_v1";

    public static ServerConfigManager getInstance(Context context) {
        if (mInstance == null)
            mInstance = new ServerConfigManager(context.getApplicationContext());
        return mInstance;
    }

    public static synchronized void destroyInstance() {
        DiagnosticLog.d("CONFIG", () -> "ServerConfigManager instance destroyed");
        mInstance = null;
    }

    private final File mServersPath;
    private final File mServerLogsPath;
    private final File mFallbackServerLogsPath;

    private final Context mContext;
    private final List<ServerConfigData> mServers = new ArrayList<>();
    private final Map<UUID, ServerConfigData> mServersMap = new HashMap<>();
    private final List<ConnectionsListener> mListeners = new ArrayList<>();
    private final Object mIOLock = new Object();

    public ServerConfigManager(Context context) {
        mContext = context.getApplicationContext();
        mServersPath = new File(mContext.getFilesDir(), SERVERS_PATH);
        File externalFilesDir = mContext.getExternalFilesDir(null);
        mFallbackServerLogsPath = new File(mContext.getFilesDir(), SERVER_LOGS_PATH);
        mServerLogsPath = externalFilesDir != null ? new File(externalFilesDir, SERVER_LOGS_PATH)
                : mFallbackServerLogsPath;
        mServerLogsPath.mkdirs();

        DiagnosticLog.i(mContext, "CONFIG", () ->
                "Server configuration manager initializing externalFilesAvailable=" +
                        (externalFilesDir != null));
        if (externalFilesDir != null && mFallbackServerLogsPath.exists()) {
            DiagnosticLog.i(mContext, "CONFIG", () -> "Migrating legacy chat-log storage");
            migrateServerLogs(mFallbackServerLogsPath, mServerLogsPath);
            mFallbackServerLogsPath.delete();
            DiagnosticLog.i(mContext, "CONFIG", () -> "Legacy chat-log migration completed");
        }

        loadServers();
        initializeBundledServers();
        DiagnosticLog.i(mContext, "CONFIG", () ->
                "Server configuration manager ready serverCount=" + mServers.size());
    }

    /**
     * Adds public starter configurations only once and only when the user has no servers yet.
     * Existing installations and restored backups remain authoritative.
     */
    private void initializeBundledServers() {
        if (mContext.getSharedPreferences(BUNDLED_SERVERS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(BUNDLED_SERVERS_INITIALIZED, false)) {
            DiagnosticLog.d(mContext, "CONFIG", () -> "Bundled server initialization already done");
            return;
        }

        if (mServers.isEmpty()) {
            DiagnosticLog.i(mContext, "CONFIG", () ->
                    "No saved servers found; creating bundled starter server");
            ServerConfigData simosnap = new ServerConfigData();
            simosnap.name = "Simosnap";
            simosnap.uuid = UUID.nameUUIDFromBytes(
                    "bundled-server:simosnap".getBytes(Charset.forName("UTF-8")));
            simosnap.address = "irc.simosnap.org";
            simosnap.port = 6697;
            simosnap.ssl = true;
            simosnap.charset = "UTF-8";
            simosnap.rejoinChannels = true;
            simosnap.hideJoinPartMessages = true;
            try {
                saveServer(simosnap);
            } catch (IOException e) {
                Log.e(TAG, "Failed to create bundled Simosnap server", e);
                DiagnosticLog.e(mContext, "CONFIG", () ->
                        "Bundled starter server creation failed", e);
                return;
            }
        }

        mContext.getSharedPreferences(BUNDLED_SERVERS_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(BUNDLED_SERVERS_INITIALIZED, true).apply();
        DiagnosticLog.d(mContext, "CONFIG", () -> "Bundled server initialization marked complete");
    }

    // NOTE: This is not synchronized; don't call it outside of the constructor
    private void loadServers() {
        File[] files = mServersPath.listFiles();
        if (files == null) {
            DiagnosticLog.d(mContext, "CONFIG", () -> "Server configuration directory is empty");
            return;
        }
        int loadedCount = 0;
        int failedCount = 0;
        for (File f : files) {
            if (!f.isFile() || !f.getName().startsWith(SERVER_FILE_PREFIX) ||
                    !f.getName().endsWith(SERVER_FILE_SUFFIX))
                continue;
            try {
                ServerConfigData data = SettingsHelper.getGson().fromJson(
                        new BufferedReader(new FileReader(f)), ServerConfigData.class);
                data.migrateLegacyProperties(ChatSettings.shouldHideJoinPartMessages());
                mServers.add(data);
                mServersMap.put(data.uuid, data);
                loadedCount++;
                final String serverId = diagnosticId(data.uuid);
                final int addressCount = data.getConnectionAddresses().size();
                final boolean ssl = data.ssl;
                final int port = data.port;
                final String authMode = data.authMode == null ? "none" : data.authMode;
                DiagnosticLog.d(mContext, "CONFIG", () ->
                        serverId + " loaded ssl=" + ssl + ", port=" + port +
                                ", addresses=" + addressCount + ", authMode=" + authMode);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load server data");
                e.printStackTrace();
                failedCount++;
                DiagnosticLog.e(mContext, "CONFIG", () ->
                        "Loading one server configuration failed", e);
            }
        }
        final int finalLoadedCount = loadedCount;
        final int finalFailedCount = failedCount;
        DiagnosticLog.i(mContext, "CONFIG", () ->
                "Server configurations loaded count=" + finalLoadedCount +
                        ", failed=" + finalFailedCount);
        List<UUID> savedOrder = loadSavedServerOrder();
        if (savedOrder != null && !savedOrder.isEmpty()) {
            applyServerOrder(savedOrder);
            DiagnosticLog.d(mContext, "CONFIG", () ->
                    "Saved server order applied entries=" + savedOrder.size());
        }
    }

    private List<UUID> loadSavedServerOrder() {
        File file = new File(mServersPath, SERVER_ORDER_FILENAME);
        if (!file.exists())
            return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            ServerOrderData orderData = SettingsHelper.getGson().fromJson(reader, ServerOrderData.class);
            if (orderData != null && orderData.order != null) {
                List<UUID> uuids = new ArrayList<>();
                int invalid = 0;
                for (String idStr : orderData.order) {
                    try {
                        uuids.add(UUID.fromString(idStr));
                    } catch (IllegalArgumentException ignored) {
                        invalid++;
                    }
                }
                final int count = uuids.size();
                final int invalidCount = invalid;
                DiagnosticLog.d(mContext, "CONFIG", () ->
                        "Saved server order loaded entries=" + count + ", invalid=" + invalidCount);
                return uuids;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load server order data", e);
            DiagnosticLog.e(mContext, "CONFIG", () -> "Loading server order failed", e);
        }
        return null;
    }

    private void applyServerOrder(List<UUID> order) {
        List<ServerConfigData> ordered = new ArrayList<>();
        for (UUID uuid : order) {
            ServerConfigData data = mServersMap.get(uuid);
            if (data != null && !ordered.contains(data)) {
                ordered.add(data);
            }
        }
        for (ServerConfigData data : mServers) {
            if (!ordered.contains(data)) {
                ordered.add(data);
            }
        }
        mServers.clear();
        mServers.addAll(ordered);
    }

    public void saveServerOrder(List<UUID> newOrder) throws IOException {
        DiagnosticLog.i(mContext, "CONFIG", () ->
                "Saving server order requested entries=" + (newOrder == null ? 0 : newOrder.size()));
        synchronized (this) {
            applyServerOrder(newOrder);
        }
        try {
            persistServerOrder();
        } catch (IOException e) {
            DiagnosticLog.e(mContext, "CONFIG", () -> "Persisting server order failed", e);
            throw e;
        }
        synchronized (mListeners) {
            for (ConnectionsListener listener : mListeners)
                listener.onServerOrderChanged();
        }
        ServerConnectionManager.getInstance(mContext).reorderConnections();
        DiagnosticLog.i(mContext, "CONFIG", () -> "Server order saved successfully");
    }

    public void persistServerOrder() throws IOException {
        List<String> orderStr = new ArrayList<>();
        synchronized (this) {
            for (ServerConfigData data : mServers) {
                orderStr.add(data.uuid.toString());
            }
        }
        synchronized (mIOLock) {
            mServersPath.mkdirs();
            ServerOrderData orderData = new ServerOrderData();
            orderData.order = orderStr;
            BufferedWriter writer = new BufferedWriter(
                    new FileWriter(new File(mServersPath, SERVER_ORDER_FILENAME)));
            SettingsHelper.getGson().toJson(orderData, writer);
            writer.close();
        }
        DiagnosticLog.d(mContext, "CONFIG", () ->
                "Server order persisted entries=" + orderStr.size());
    }

    private void migrateServerLogs(File from, File to) {
        File[] files = from.listFiles();
        if (files == null)
            return;
        to.mkdir();
        for (File file : files) {
            if (file.isDirectory()) {
                migrateServerLogs(file, new File(to, file.getName()));
            } else {
                File toFile = new File(to, file.getName());
                File tempFile = new File(to, file.getName() + ".tmp");
                try {
                    FileInputStream fis = new FileInputStream(file);
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = fis.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                    fis.close();
                    fos.close();
                } catch (IOException e) {
                    DiagnosticLog.e(mContext, "CONFIG", () ->
                            "Legacy chat-log migration failed", e);
                    throw new RuntimeException("Migration failed", e);
                }
                if (toFile.exists())
                    toFile.delete();
                tempFile.renameTo(toFile);
            }
            file.delete();
        }
    }

    public List<ServerConfigData> getServers() {
        synchronized (this) {
            return new ArrayList<>(mServers);
        }
    }

    public ServerConfigData findServer(UUID uuid) {
        synchronized (this) {
            return mServersMap.get(uuid);
        }
    }

    public void saveServer(ServerConfigData data) throws IOException {
        saveServer(data, true);
    }

    /**
     * Persists configuration that does not alter IRC connection parameters.
     * This deliberately avoids broadcasting a connection update to listeners.
     */
    public void saveServerConfiguration(ServerConfigData data) throws IOException {
        saveServer(data, false);
    }

    private void saveServer(ServerConfigData data, boolean notifyListeners) throws IOException {
        final String serverId = diagnosticId(data == null ? null : data.uuid);
        final boolean ssl = data != null && data.ssl;
        final int port = data == null ? -1 : data.port;
        final int addressCount = data == null ? 0 : data.getConnectionAddresses().size();
        final String authMode = data == null || data.authMode == null ? "none" : data.authMode;
        DiagnosticLog.i(mContext, "CONFIG", () ->
                serverId + " save requested notifyListeners=" + notifyListeners +
                        ", ssl=" + ssl + ", port=" + port + ", addresses=" + addressCount +
                        ", authMode=" + authMode);
        boolean existed = false;
        synchronized (this) {
            if (mServersMap.containsKey(data.uuid)) {
                existed = true;
                int index = mServers.indexOf(mServersMap.get(data.uuid));
                if (index != -1) {
                    mServers.set(index, data);
                } else {
                    mServers.add(data);
                }
            } else {
                mServers.add(data);
            }
            mServersMap.put(data.uuid, data);
        }
        try {
            File orderFile = new File(mServersPath, SERVER_ORDER_FILENAME);
            if (orderFile.exists()) {
                persistServerOrder();
            }
            synchronized (mIOLock) {
                mServersPath.mkdirs();
                BufferedWriter writer = new BufferedWriter(new FileWriter(
                        new File(mServersPath, SERVER_FILE_PREFIX + data.uuid.toString() +
                                SERVER_FILE_SUFFIX)));
                SettingsHelper.getGson().toJson(data, writer);
                writer.close();
            }
        } catch (IOException e) {
            DiagnosticLog.e(mContext, "CONFIG", () -> serverId + " save failed", e);
            throw e;
        }
        final boolean wasExisting = existed;
        DiagnosticLog.i(mContext, "CONFIG", () ->
                serverId + " saved existed=" + wasExisting +
                        ", notifyListeners=" + notifyListeners);
        if (!notifyListeners)
            return;
        synchronized (mListeners) {
            if (existed) {
                for (ConnectionsListener listener : mListeners)
                    listener.onConnectionUpdated(data);
            } else {
                for (ConnectionsListener listener : mListeners)
                    listener.onConnectionAdded(data);
            }
        }
    }

    public void deleteServer(ServerConfigData data, boolean deleteLogs) {
        final String serverId = diagnosticId(data == null ? null : data.uuid);
        DiagnosticLog.i(mContext, "CONFIG", () ->
                serverId + " delete requested deleteLogs=" + deleteLogs);
        ServerConnectionManager.getInstance(mContext).killDisconnectingConnection(data.uuid);
        synchronized (this) {
            mServers.remove(data);
            mServersMap.remove(data.uuid);
        }
        File orderFile = new File(mServersPath, SERVER_ORDER_FILENAME);
        if (orderFile.exists()) {
            if (mServers.isEmpty()) {
                boolean removed = orderFile.delete();
                final boolean orderRemoved = removed;
                DiagnosticLog.d(mContext, "CONFIG", () ->
                        "Removed empty server order file success=" + orderRemoved);
            } else {
                try {
                    persistServerOrder();
                } catch (IOException e) {
                    DiagnosticLog.w(mContext, "CONFIG", () ->
                            serverId + " order persistence failed during delete", e);
                }
            }
        }
        synchronized (mIOLock) {
            File file = new File(mServersPath, SERVER_FILE_PREFIX + data.uuid.toString() + SERVER_FILE_SUFFIX);
            boolean configDeleted = file.delete();
            file = getServerSSLCertsFile(data.uuid);
            boolean certDeleted = !file.exists() || file.delete();
            int logFilesDeleted = 0;
            if (deleteLogs) {
                file = getServerChatLogDir(data.uuid);
                if (file.exists()) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.delete())
                                logFilesDeleted++;
                        }
                    }
                    file.delete();
                }
            }
            final boolean configRemoved = configDeleted;
            final boolean certRemoved = certDeleted;
            final int deletedLogs = logFilesDeleted;
            DiagnosticLog.d(mContext, "CONFIG", () ->
                    serverId + " files deleted config=" + configRemoved +
                            ", certStore=" + certRemoved + ", logFiles=" + deletedLogs);
        }
        synchronized (mListeners) {
            for (ConnectionsListener listener : mListeners)
                listener.onConnectionRemoved(data);
        }
        NotificationCountStorage.getInstance(mContext).requestRemoveServerCounters(data.uuid);
        DiagnosticLog.i(mContext, "CONFIG", () -> serverId + " delete completed");
    }

    public void deleteServer(ServerConfigData data) {
        deleteServer(data, true);
    }

    public void deleteAllServers(boolean deleteLogs) {
        final int count = mServers.size();
        DiagnosticLog.i(mContext, "CONFIG", () ->
                "Deleting all servers count=" + count + ", deleteLogs=" + deleteLogs);
        synchronized (this) {
            while (mServers.size() > 0)
                deleteServer(mServers.get(mServers.size() - 1), deleteLogs);
        }
        File orderFile = new File(mServersPath, SERVER_ORDER_FILENAME);
        if (orderFile.exists()) {
            orderFile.delete();
        }
        DiagnosticLog.i(mContext, "CONFIG", () -> "Delete all servers completed");
    }

    public File getServerSSLCertsFile(UUID uuid) {
        return new File(mServersPath, SERVER_CERTS_FILE_PREFIX + uuid.toString() + SERVER_CERTS_FILE_SUFFIX);
    }

    public File getChatLogDir() {
        return mServerLogsPath;
    }

    public File getServerChatLogDir(UUID uuid) {
        return new File(mServerLogsPath, uuid.toString());
    }

    public File getServerMiscDataFile(UUID uuid) {
        return new File(getServerChatLogDir(uuid), SERVER_MISC_DATA_FILENAME);
    }

    public void addListener(ConnectionsListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    public void removeListener(ConnectionsListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    private static String diagnosticId(UUID uuid) {
        return DiagnosticLog.pseudonym("server-config", uuid == null ? "unknown" : uuid.toString());
    }

    public interface ConnectionsListener {

        void onConnectionAdded(ServerConfigData data);

        void onConnectionRemoved(ServerConfigData data);

        void onConnectionUpdated(ServerConfigData data);

        default void onServerOrderChanged() {
        }

    }

    public static class ServerOrderData {
        public List<String> order;
    }

}

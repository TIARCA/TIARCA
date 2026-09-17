package io.mrarm.irc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import io.mrarm.irc.util.DefaultPreferences;
import androidx.activity.result.ActivityResultLauncher;
import androidx.documentfile.provider.DocumentFile;
import androidx.appcompat.app.AlertDialog;

import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.irc.dcc.DCCClient;
import io.mrarm.chatlib.irc.dcc.DCCClientManager;
import io.mrarm.chatlib.irc.dcc.DCCReverseClient;
import io.mrarm.chatlib.irc.dcc.DCCServer;
import io.mrarm.chatlib.irc.dcc.DCCServerManager;
import io.mrarm.chatlib.irc.dcc.DCCUtils;
import io.mrarm.irc.upnp.PortMapper;
import io.mrarm.irc.upnp.rpc.AddPortMappingCall;
import io.mrarm.irc.util.FormatUtils;
import io.mrarm.irc.util.AppExecutors;
import io.mrarm.irc.util.DiagnosticLog;

public class DCCManager implements DCCServerManager.UploadListener, DCCClient.CloseListener,
        DCCReverseClient.StateListener {

    private static DCCManager sInstance;

    private static final String PREF_DCC_ASKED_FOR_PERMISSION = "dcc_storage_permission_asked";
    private static final String PREF_DCC_ALWAYS_USE_APP_DOWNLOAD_DIR = "dcc_force_application_download_directory";
    private static final String PREF_DCC_DIRECTORY_OVERRIDE_URI = "dcc_download_directory_uri";
    private static final String PREF_DCC_DIRECTORY_OVERRIDE_URI_SYSTEM = "dcc_download_directory_uri_system";

    public static DCCManager getInstance(Context context) {
        if (sInstance == null)
            sInstance = new DCCManager(context.getApplicationContext());
        return sInstance;
    }

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final DCCServerManager mServer;
    private final DCCHistory mHistory;
    private final Map<DCCServer, DCCServerManager.UploadEntry> mUploads = new HashMap<>();
    private final Map<DCCServerManager.UploadEntry, UploadServerInfo> mUploadServers = new HashMap<>();
    private final Map<DCCServerManager.UploadEntry, PortMapper.PortMappingResult> mUploadPortMappings = new HashMap<>();
    private final List<DCCServer.UploadSession> mSessions = new ArrayList<>();
    private final List<DownloadInfo> mDownloads = new ArrayList<>();
    private final List<DownloadListener> mDownloadListeners = new ArrayList<>();
    private File mDownloadDirectory;
    private Uri mDownloadDirectoryOverrideURI;
    private boolean mIsDownloadDirectoryOverrideURISystem;
    private final File mFallbackDownloadDirectory;
    private boolean mAlwaysUseFallbackDir;
    private boolean mUseSystemDirectoryViaControlResolver;
    private boolean mHasSystemDirectoryAccess;
    private final DCCNotificationManager mNotificationManager;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public DCCManager(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = DefaultPreferences.get(mContext);
        mNotificationManager = new DCCNotificationManager(mContext);
        mFallbackDownloadDirectory = mContext.getExternalFilesDir("downloads");
        mDownloadDirectory = mFallbackDownloadDirectory;
        mHistory = new DCCHistory(mContext);
        mServer = new DCCServerManager();
        mServer.addUploadListener(this);
        mServer.addUploadListener(mNotificationManager);
        addDownloadListener(mNotificationManager);
        mAlwaysUseFallbackDir = mPreferences.getBoolean(PREF_DCC_ALWAYS_USE_APP_DOWNLOAD_DIR, false);
        String uri = mPreferences.getString(PREF_DCC_DIRECTORY_OVERRIDE_URI, null);
        if (uri != null) {
            mDownloadDirectoryOverrideURI = Uri.parse(uri);
            mIsDownloadDirectoryOverrideURISystem = mPreferences.getBoolean(
                    PREF_DCC_DIRECTORY_OVERRIDE_URI_SYSTEM, false);
        }
        checkSystemDownloadsDirectoryAccess();
        DiagnosticLog.i(mContext, "DCC", () ->
                "DCC manager initialized fallbackOnly=" + mAlwaysUseFallbackDir +
                        ", overrideConfigured=" + (mDownloadDirectoryOverrideURI != null));
    }

    public void setAlwaysUseApplicationDownloadDirectory(boolean value) {
        mAlwaysUseFallbackDir = value;
        mPreferences.edit()
                .putBoolean(PREF_DCC_ALWAYS_USE_APP_DOWNLOAD_DIR, value)
                .apply();
        DiagnosticLog.i(mContext, "DCC", () ->
                "Download storage fallback-only changed value=" + value);
        checkSystemDownloadsDirectoryAccess();
    }

    public void setOverrideDownloadDirectory(Uri uri, boolean isSystem) {
        mDownloadDirectoryOverrideURI = uri;
        mIsDownloadDirectoryOverrideURISystem = isSystem;
        mPreferences.edit()
                .putString(PREF_DCC_DIRECTORY_OVERRIDE_URI, uri.toString())
                .putBoolean(PREF_DCC_DIRECTORY_OVERRIDE_URI_SYSTEM, isSystem)
                .apply();
        final String scheme = uri == null ? "none" : uri.getScheme();
        DiagnosticLog.i(mContext, "DCC", () ->
                "Download directory override changed isSystem=" + isSystem +
                        ", scheme=" + scheme);
    }

    public Uri getDownloadDirectoryOverrideURI() {
        if (mAlwaysUseFallbackDir)
            return null;
        return mDownloadDirectoryOverrideURI;
    }

    public boolean isDownloadDirectoryOverrideURISystem() {
        return mIsDownloadDirectoryOverrideURISystem;
    }

    public boolean isSystemDownloadDirectoryUsed() {
        return (mHasSystemDirectoryAccess || mUseSystemDirectoryViaControlResolver) &&
                (mDownloadDirectoryOverrideURI == null || mIsDownloadDirectoryOverrideURISystem);
    }

    private void checkSystemDownloadsDirectoryAccess() {
        if (mDownloadDirectoryOverrideURI != null && !mAlwaysUseFallbackDir) {
            DocumentFile dir = DocumentFile.fromTreeUri(mContext,
                    mDownloadDirectoryOverrideURI);
            mHasSystemDirectoryAccess = dir != null && dir.exists() && dir.canWrite();
            mUseSystemDirectoryViaControlResolver = false;
            final boolean writable = mHasSystemDirectoryAccess;
            DiagnosticLog.d(mContext, "DCC", () ->
                    "Download storage checked mode=override writable=" + writable);
            return;
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && downloadsDir.canWrite() && !mAlwaysUseFallbackDir) {
            mDownloadDirectory = downloadsDir;
            mHasSystemDirectoryAccess = true;
            mUseSystemDirectoryViaControlResolver = false;
        } else {
            mDownloadDirectory = mFallbackDownloadDirectory;
            mHasSystemDirectoryAccess = false;
            mUseSystemDirectoryViaControlResolver = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        }
        final boolean directAccess = mHasSystemDirectoryAccess;
        final boolean mediaStore = mUseSystemDirectoryViaControlResolver;
        final boolean fallback = mDownloadDirectory == mFallbackDownloadDirectory;
        DiagnosticLog.d(mContext, "DCC", () ->
                "Download storage checked directAccess=" + directAccess +
                        ", mediaStore=" + mediaStore + ", fallback=" + fallback);
        Log.d("DCCManager", "Download directory: " +
                (mDownloadDirectory != null ? mDownloadDirectory.getAbsolutePath() : "null"));
    }

    public boolean needsAskSystemDownloadsPermission() {
        if (!mHasSystemDirectoryAccess)
            checkSystemDownloadsDirectoryAccess();
        boolean needsPermission = !mHasSystemDirectoryAccess &&
                !mUseSystemDirectoryViaControlResolver &&
                !mPreferences.getBoolean(PREF_DCC_ASKED_FOR_PERMISSION, false);
        DiagnosticLog.d(mContext, "DCC", () ->
                "Download storage permission check needsPrompt=" + needsPermission);
        return needsPermission;
    }

    public DCCNotificationManager getNotificationManager() {
        return mNotificationManager;
    }

    public DCCServerManager getServer() {
        return mServer;
    }

    public DCCHistory getHistory() {
        return mHistory;
    }

    public DCCClientManager createClient(ServerConnectionInfo server) {
        DiagnosticLog.d(mContext, "DCC", () ->
                serverDiagnosticId(server) + " DCC client manager created");
        return new ClientImpl(server);
    }

    public void addDownloadListener(DownloadListener listener) {
        synchronized (mDownloads) {
            mDownloadListeners.add(listener);
        }
    }

    public void removeDownloadListener(DownloadListener listener) {
        synchronized (mDownloads) {
            mDownloadListeners.remove(listener);
        }
    }

    @Override
    public void onUploadCreated(DCCServerManager.UploadEntry uploadEntry) {
        synchronized (mUploads) {
            mUploads.put(uploadEntry.getServer(), uploadEntry);

            ServerConnectionData connection = uploadEntry.getConnection();
            ServerConnectionInfo connectionInfo = null;
            for (ServerConnectionInfo info : ServerConnectionManager.getInstance(mContext)
                    .getConnections()) {
                if (((ServerConnectionApi) info.getApiInstance()).getServerConnectionData()
                        == connection) {
                    connectionInfo = info;
                    break;
                }
            }
            mUploadServers.put(uploadEntry, connectionInfo != null
                    ? new UploadServerInfo(connectionInfo) : null);
        }
        final String uploadId = uploadDiagnosticId(uploadEntry);
        final boolean reverse = uploadEntry.getReverseId() != -1;
        DiagnosticLog.i(mContext, "DCC", () ->
                uploadId + " upload entry created reverse=" + reverse +
                        ", listenerPort=" + uploadEntry.getPort());
    }

    @Override
    public void onUploadDestroyed(DCCServerManager.UploadEntry uploadEntry) {
        final String uploadId = uploadDiagnosticId(uploadEntry);
        DiagnosticLog.i(mContext, "DCC", () -> uploadId + " upload entry destroyed");
        synchronized (mUploads) {
            mUploads.remove(uploadEntry.getServer());
            mUploadServers.remove(uploadEntry);

            PortMapper.PortMappingResult mapping;
            if ((mapping = mUploadPortMappings.remove(uploadEntry)) != null) {
                if (Thread.currentThread() == Looper.getMainLooper().getThread())
                    AppExecutors.IO.execute(() -> deleteUploadPortMapping(mapping));
                else
                    deleteUploadPortMapping(mapping);
            }
        }
    }

    private void deleteUploadPortMapping(PortMapper.PortMappingResult mapping) {
        final int externalPort = mapping.getExternalPort();
        try {
            DiagnosticLog.d(mContext, "DCC", () ->
                    "Removing DCC UPnP mapping externalPort=" + externalPort);
            PortMapper.removePortMapping(mapping);
        } catch (Exception e) {
            Log.w("DCCManager", "Failed to remove port mapping for port " + externalPort);
            e.printStackTrace();
            DiagnosticLog.w(mContext, "DCC", () ->
                    "Removing DCC UPnP mapping failed externalPort=" + externalPort, e);
        }
    }

    @Override
    public void onSessionCreated(DCCServer dccServer, DCCServer.UploadSession uploadSession) {
        synchronized (mSessions) {
            mSessions.add(uploadSession);
        }
        DCCServerManager.UploadEntry entry = getUploadEntry(dccServer);
        final String uploadId = entry == null ? "dcc-upload-unknown" : uploadDiagnosticId(entry);
        final long total = uploadSession.getTotalSize();
        DiagnosticLog.i(mContext, "DCC", () ->
                uploadId + " upload session connected totalBytes=" + total);
    }

    @Override
    public void onSessionDestroyed(DCCServer dccServer, DCCServer.UploadSession uploadSession) {
        DCCServerManager.UploadEntry entry;
        UploadServerInfo uploadServerInfo;
        boolean shouldClose;
        synchronized (mSessions) {
            mSessions.remove(uploadSession);
            shouldClose = uploadSession.getAcknowledgedSize() >= uploadSession.getTotalSize();
            if (shouldClose) {
                for (DCCServer.UploadSession s : mSessions) {
                    if (s.getServer() == dccServer) {
                        shouldClose = false;
                        break;
                    }
                }
            }
        }
        synchronized (mUploads) {
            entry = mUploads.get(dccServer);
            uploadServerInfo = mUploadServers.get(entry);
        }
        final String uploadId = entry == null ? "dcc-upload-unknown" : uploadDiagnosticId(entry);
        final long acknowledged = uploadSession.getAcknowledgedSize();
        final long total = uploadSession.getTotalSize();
        final boolean completed = acknowledged >= total;
        DiagnosticLog.i(mContext, "DCC", () ->
                uploadId + " upload session closed acknowledgedBytes=" + acknowledged +
                        ", totalBytes=" + total + ", completed=" + completed);
        if (shouldClose && entry != null)
            mHandler.post(() -> mServer.cancelUpload(entry));
        if (entry != null && uploadServerInfo != null)
            mHistory.addEntry(new DCCHistory.Entry(entry, uploadSession, uploadServerInfo,
                    new Date()));
    }

    public void onDownloadCreated(DownloadInfo download) {
        DiagnosticLog.i(mContext, "DCC", () ->
                download.diagnosticId() + " incoming offer queued reverse=" + download.isReverse() +
                        ", sizeBytes=" + download.getFileSize());
        synchronized (mDownloads) {
            mDownloads.add(download);
            for (DownloadListener listener : mDownloadListeners)
                listener.onDownloadCreated(download);
        }
    }

    public void onDownloadDestroyed(DownloadInfo download) {
        DiagnosticLog.i(mContext, "DCC", () ->
                download.diagnosticId() + " download removed pending=" + download.isPending() +
                        ", cancelled=" + download.mCancelled);
        synchronized (mDownloads) {
            mDownloads.remove(download);
            for (DownloadListener listener : mDownloadListeners)
                listener.onDownloadDestroyed(download);
        }
        mHistory.addEntry(new DCCHistory.Entry(download, new Date()));
    }

    @Override
    public void onClosed(DCCClient dccClient) {
        DownloadInfo download = null;
        synchronized (mDownloads) {
            for (int i = mDownloads.size() - 1; i >= 0; --i) {
                download = mDownloads.get(i);
                if (download.getClient() == dccClient) {
                    mDownloads.remove(i);
                    for (DownloadListener listener : mDownloadListeners)
                        listener.onDownloadDestroyed(download);
                    break;
                }
            }
        }
        if (download != null) {
            final DownloadInfo closedDownload = download;
            DiagnosticLog.i(mContext, "DCC", () ->
                    closedDownload.diagnosticId() + " direct download client closed");
            mHistory.addEntry(new DCCHistory.Entry(download, new Date()));
        }
    }

    @Override
    public void onClosed(DCCReverseClient dccReverseClient) {
        DownloadInfo download = null;
        synchronized (mDownloads) {
            for (int i = mDownloads.size() - 1; i >= 0; --i) {
                download = mDownloads.get(i);
                if (download.getReverseClient() == dccReverseClient) {
                    mDownloads.remove(i);
                    for (DownloadListener listener : mDownloadListeners)
                        listener.onDownloadDestroyed(download);
                    break;
                }
            }
        }
        if (download != null) {
            final DownloadInfo closedDownload = download;
            DiagnosticLog.i(mContext, "DCC", () ->
                    closedDownload.diagnosticId() + " reverse download listener closed");
            mHistory.addEntry(new DCCHistory.Entry(download, new Date()));
        }
    }

    @Override
    public void onClientConnected(DCCReverseClient dccReverseClient, DCCClient dccClient) {
        synchronized (mDownloads) {
            for (DownloadInfo download : mDownloads) {
                if (download.getReverseClient() == dccReverseClient) {
                    DiagnosticLog.i(mContext, "DCC", () ->
                            download.diagnosticId() + " reverse download peer connected");
                    for (DownloadListener listener : mDownloadListeners)
                        listener.onDownloadUpdated(download);
                    return;
                }
            }
        }
        DiagnosticLog.w(mContext, "DCC", () ->
                "Reverse download peer connected but transfer was not found", null);
    }

    public DCCServerManager.UploadEntry getUploadEntry(DCCServer server) {
        synchronized (mUploads) {
            return mUploads.get(server);
        }
    }

    public UploadServerInfo getUploadServerInfo(DCCServerManager.UploadEntry upload) {
        synchronized (mUploads) {
            return mUploadServers.get(upload);
        }
    }

    public String getUploadName(DCCServer server) {
        synchronized (mUploads) {
            DCCServerManager.UploadEntry ent = mUploads.get(server);
            if (ent == null)
                return null;
            return ent.getFileName();
        }
    }

    public List<DCCServerManager.UploadEntry> getUploads() {
        synchronized (mUploads) {
            return new ArrayList<>(mUploads.values());
        }
    }

    public List<DCCServer.UploadSession> getUploadSessions() {
        synchronized (mSessions) {
            return new ArrayList<>(mSessions);
        }
    }

    public boolean hasAnyDownloads() {
        synchronized (mSessions) {
            return !mDownloads.isEmpty();
        }
    }

    public boolean hasAnyActiveDownloads() {
        synchronized (mSessions) {
            for (DownloadInfo download : mDownloads) {
                if (!download.isPending())
                    return true;
            }
            return false;
        }
    }

    public List<DownloadInfo> getDownloads() {
        synchronized (mDownloads) {
            return new ArrayList<>(mDownloads);
        }
    }

    public void startUpload(ServerConnectionInfo server, String channel,
                            DCCServer.FileChannelFactory file, String fileName, long fileSize) {
        ServerConnectionData connectionData = ((ServerConnectionApi) server.getApiInstance())
                .getServerConnectionData();
        final boolean wifi = ServerConnectionManager.isWifiConnected(mContext);
        final String serverId = serverDiagnosticId(server);
        final String transferId = DiagnosticLog.pseudonym("dcc-upload",
                (channel == null ? "" : channel) + "|" + (fileName == null ? "" : fileName));
        final String targetId = DiagnosticLog.pseudonym("target", channel);
        DiagnosticLog.i(mContext, "DCC", () ->
                transferId + " upload requested server=" + serverId + ", target=" + targetId +
                        ", wifi=" + wifi + ", sizeBytes=" + fileSize);

        if (wifi) {
            AppExecutors.IO.execute(() -> {
                DCCServerManager.UploadEntry upload = null;
                PortMapper.PortMappingResult mapping = null;
                try {
                    DiagnosticLog.d(mContext, "DCC", () ->
                            transferId + " creating direct upload listener");
                    upload = mServer.startUpload(connectionData, channel, fileName, file);
                    final int listenerPort = upload.getPort();
                    DiagnosticLog.i(mContext, "DCC", () ->
                            transferId + " direct listener ready port=" + listenerPort);
                    DiagnosticLog.d(mContext, "DCC", () ->
                            transferId + " requesting UPnP port mapping");
                    mapping = PortMapper.mapPort(new PortMapper.PortMappingRequest(
                            AddPortMappingCall.PROTOCOL_TCP, upload.getPort(),
                            upload.getPort(), "TIARCA IRC DCC transfer"));
                    synchronized (mUploads) {
                        if (!mUploads.containsKey(upload.getServer()))
                            throw new IOException("Upload cancelled while we were setting up" +
                                    " port mapping");
                        mUploadPortMappings.put(upload, mapping);
                    }
                    final int externalPort = mapping.getExternalPort();
                    DiagnosticLog.i(mContext, "DCC", () ->
                            transferId + " UPnP mapping ready externalPort=" + externalPort);
                    mServer.setUploadPortForwarded(upload, externalPort);
                    server.getApiInstance().sendMessage(channel, DCCUtils.buildSendMessage(
                            mapping.getExternalIP(), fileName, externalPort, fileSize),
                            null, null);
                    DiagnosticLog.i(mContext, "DCC", () ->
                            transferId + " DCC SEND offer sent mode=direct");
                } catch (IOException e) {
                    e.printStackTrace();
                    DiagnosticLog.w(mContext, "DCC", () ->
                            transferId + " direct upload setup failed; falling back to reverse", e);

                    mHandler.post(() -> Toast
                            .makeText(mContext, R.string.error_generic, Toast.LENGTH_SHORT).show());
                    if (upload != null)
                        mServer.cancelUpload(upload);
                    if (mapping != null) {
                        final int externalPort = mapping.getExternalPort();
                        try {
                            PortMapper.removePortMapping(mapping);
                        } catch (Exception e2) {
                            Log.w("DCCManager", "Failed to remove port mapping " +
                                    "in error handler");
                            e2.printStackTrace();
                            DiagnosticLog.w(mContext, "DCC", () ->
                                    transferId + " cleanup after failed mapping failed externalPort=" +
                                            externalPort, e2);
                        }
                    }

                    // fall back to reverse DCC
                    upload = mServer.addReverseUpload(connectionData, channel, fileName, file);
                    final int reverseId = upload.getReverseId();
                    DiagnosticLog.i(mContext, "DCC", () ->
                            transferId + " reverse upload registered id=" + reverseId +
                                    " reason=direct_setup_failed");
                    server.getApiInstance().sendMessage(channel, DCCUtils.buildSendMessage(
                            "127.0.0.1", fileName, 0, fileSize, reverseId),
                            null, null);
                    DiagnosticLog.i(mContext, "DCC", () ->
                            transferId + " DCC SEND offer sent mode=reverse");
                }
            });
        } else {
            // fall back to reverse DCC
            DCCServerManager.UploadEntry upload = mServer.addReverseUpload(
                    connectionData, channel, fileName, file);
            final int reverseId = upload.getReverseId();
            DiagnosticLog.i(mContext, "DCC", () ->
                    transferId + " reverse upload registered id=" + reverseId +
                            " reason=not_wifi");
            server.getApiInstance().sendMessage(channel, DCCUtils.buildSendMessage(
                    "0.0.0.0", fileName, 0, fileSize,
                    reverseId),
                    null, null);
            DiagnosticLog.i(mContext, "DCC", () ->
                    transferId + " DCC SEND offer sent mode=reverse");
        }
    }

    private static String serverDiagnosticId(ServerConnectionInfo server) {
        if (server == null || server.getUUID() == null)
            return "server-unknown";
        return DiagnosticLog.pseudonym("server", server.getUUID().toString());
    }

    private static String uploadDiagnosticId(DCCServerManager.UploadEntry upload) {
        if (upload == null)
            return "dcc-upload-unknown";
        return DiagnosticLog.pseudonym("dcc-upload",
                String.valueOf(upload.getUser()) + "|" + String.valueOf(upload.getFileName()));
    }


    public class UploadServerInfo {

        private final UUID mServerUUID;
        private final String mServerName;

        public UploadServerInfo(UUID uuid, String serverName) {
            this.mServerUUID = uuid;
            this.mServerName = serverName;
        }
        public UploadServerInfo(ServerConnectionInfo connectionInfo) {
            this.mServerUUID = connectionInfo.getUUID();
            this.mServerName = connectionInfo.getName();
        }

        public UUID getServerUUID() {
            return mServerUUID;
        }

        public String getServerName() {
            return mServerName;
        }

    }

    public class DownloadInfo {

        private final UUID mServerUUID;
        private final String mServerName;
        private final MessagePrefix mSender;
        private final String mFileName;
        private final long mFileSize;
        private final String mAddress;
        private final int mPort;
        private final int mReverseUploadId;
        private boolean mPending = true;
        private boolean mCancelled = false;
        private DCCClient mClient;
        private DCCReverseClient mReverseClient;
        private Uri mDownloadedTo;

        private DownloadInfo(ServerConnectionInfo server, MessagePrefix sender, String fileName,
                             long fileSize, String address, int port) {
            mServerUUID = server.getUUID();
            mServerName = server.getName();
            mSender = sender;
            mFileName = fileName;
            mFileSize = fileSize;
            mAddress = address;
            mPort = port;
            mReverseUploadId = -1;
        }
        private DownloadInfo(ServerConnectionInfo server, MessagePrefix sender, String fileName,
                             long fileSize, int reverseUploadId) {
            mServerUUID = server.getUUID();
            mServerName = server.getName();
            mSender = sender;
            mFileName = fileName;
            mFileSize = fileSize;
            mAddress = null;
            mPort = -1;
            mReverseUploadId = reverseUploadId;
        }

        private String diagnosticId() {
            String senderNick = mSender == null ? "" : mSender.getNick();
            return DiagnosticLog.pseudonym("dcc-download",
                    String.valueOf(mServerUUID) + "|" + senderNick + "|" +
                            String.valueOf(mFileName));
        }

        public String getServerName() {
            return mServerName;
        }

        public UUID getServerUUID() {
            return mServerUUID;
        }

        public MessagePrefix getSender() {
            return mSender;
        }

        public String getRawFileName() {
            return mFileName;
        }

        public String getUnescapedFileName() {
            return DCCUtils.unescapeFilename(mFileName);
        }

        private String getFileExtension() {
            String fileName = getUnescapedFileName();
            int iof = fileName.lastIndexOf('.');
            if (iof == -1)
                return null;
            return fileName.substring(iof + 1);
        }

        public long getFileSize() {
            return mFileSize;
        }

        public synchronized Uri getDownloadedTo() {
            return mDownloadedTo;
        }

        public boolean isPending() {
            return mPending;
        }

        public boolean isReverse() {
            return mReverseUploadId != -1;
        }

        public synchronized DCCClient getClient() {
            if (mReverseClient != null)
                return mReverseClient.getClient();
            return mClient;
        }

        public synchronized DCCReverseClient getReverseClient() {
            return mReverseClient;
        }

        // The FileChannel is owned by the DCC client and closing it also closes the stream.
        @SuppressLint("Recycle")
        private void createClient() throws IOException {
            ServerConnectionInfo connection = ServerConnectionManager.getInstance(mContext)
                    .getConnection(mServerUUID);
            if (connection == null)
                throw new IOException("The connection doesn't exist");
            DiagnosticLog.i(mContext, "DCC", () ->
                    diagnosticId() + " preparing download reverse=" + isReverse() +
                            ", sizeBytes=" + mFileSize);
            FileChannel file;
            String downloadFileName = getUnescapedFileName().replace('/', '_');
            String ext = getFileExtension();
            Uri downloadUri = null;
            if (mUseSystemDirectoryViaControlResolver && mDownloadDirectoryOverrideURI == null &&
                    !mAlwaysUseFallbackDir && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues fileContent = new ContentValues();
                fileContent.put(MediaStore.Downloads.DISPLAY_NAME, downloadFileName);
                fileContent.put(MediaStore.Downloads.SIZE, getFileSize());

                downloadUri = mContext.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, fileContent);
            } if (mDownloadDirectoryOverrideURI != null && !mAlwaysUseFallbackDir) {
                DocumentFile dir = DocumentFile.fromTreeUri(mContext,
                        mDownloadDirectoryOverrideURI);
                if (dir == null)
                    throw new IOException("Download directory URI cannot be resolved");
                String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        : null;
                if (mime == null)
                    mime = "application/octet-stream";
                DocumentFile docFile = dir.createFile(mime, downloadFileName);
                if (docFile == null)
                    throw new IOException("Unable to create destination document");
                downloadUri = docFile.getUri();
            }

            if (downloadUri != null) {
                OutputStream stream = mContext.getContentResolver().openOutputStream(downloadUri);
                if (!(stream instanceof FileOutputStream))
                    throw new IOException("stream is not a file");
                file = ((FileOutputStream) stream).getChannel();
                synchronized (this) {
                    mDownloadedTo = downloadUri;
                }
                DiagnosticLog.d(mContext, "DCC", () ->
                        diagnosticId() + " download destination opened mode=content-resolver");
                Log.d("DCCManager", "Starting a download: " + downloadUri.toString());
            } else {
                if (mDownloadDirectory == null)
                    throw new IOException("Download directory is null");
                File filePath = new File(mDownloadDirectory, downloadFileName);
                int attempt = 1;
                while (filePath.exists()) {
                    filePath = new File(mDownloadDirectory, (ext != null
                            ? downloadFileName.substring(0,
                            downloadFileName.length() - ext.length() - 1)
                            : downloadFileName) + " (" + attempt + ")" +
                            (ext != null ? "." + ext : ""));
                    attempt++;
                }
                file = new FileOutputStream(filePath).getChannel();
                synchronized (this) {
                    mDownloadedTo = Uri.fromFile(filePath);
                }
                final int collisionAttempts = attempt - 1;
                DiagnosticLog.d(mContext, "DCC", () ->
                        diagnosticId() + " download destination opened mode=file collisionRenames=" +
                                collisionAttempts);
                Log.d("DCCManager", "Starting a download: " + filePath.getAbsolutePath());
            }

            try {
                if (isReverse()) {
                    synchronized (this) {
                        if (mCancelled)
                            throw new CancelledException();
                        mReverseClient = new DCCReverseClient(file, 0L, mFileSize);
                    }
                    mReverseClient.setStateListener(DCCManager.this);
                    int port = mReverseClient.createServerSocket();
                    final int listenerPort = port;
                    DiagnosticLog.i(mContext, "DCC", () ->
                            diagnosticId() + " reverse download listener ready port=" + listenerPort);
                    String message = DCCUtils.buildSendMessage(getLocalIP(), mFileName, port,
                            mFileSize, mReverseUploadId);
                    connection.getApiInstance().sendMessage(mSender.getNick(), message, null, null);
                    DiagnosticLog.i(mContext, "DCC", () ->
                            diagnosticId() + " reverse download response offer sent");
                } else {
                    DiagnosticLog.d(mContext, "DCC", () ->
                            diagnosticId() + " opening direct peer socket port=" + mPort);
                    SocketChannel socket = SocketChannel.open(
                            new InetSocketAddress(mAddress, mPort));
                    synchronized (this) {
                        if (mCancelled)
                            throw new CancelledException();
                        mClient = new DCCClient(file, 0L, mFileSize);
                    }
                    mClient.setCloseListener(DCCManager.this);
                    mClient.start(socket);
                    DiagnosticLog.i(mContext, "DCC", () ->
                            diagnosticId() + " direct download client started");
                }
            } catch (Exception e) {
                DiagnosticLog.e(mContext, "DCC", () ->
                        diagnosticId() + " download client setup failed reverse=" + isReverse(), e);
                try {
                    file.close();
                } catch (IOException closeError) {
                    DiagnosticLog.w(mContext, "DCC", () ->
                            diagnosticId() + " closing failed download destination failed", closeError);
                }
                synchronized (this) {
                    if (mClient != null)
                        mClient.close();
                    mClient = null;
                    if (mReverseClient != null)
                        mReverseClient.close();
                    mReverseClient = null;
                }
                throw e;
            }
        }

        public void approve() {
            if (!mPending || mCancelled)
                return;
            mPending = false;
            DiagnosticLog.i(mContext, "DCC", () -> diagnosticId() + " incoming offer accepted");
            AppExecutors.IO.execute(() -> {
                try {
                    createClient();
                } catch (CancelledException e) {
                    DiagnosticLog.i(mContext, "DCC", () ->
                            diagnosticId() + " download cancelled during setup");
                    onDownloadDestroyed(this);
                    return;
                } catch (IOException e) {
                    mHandler.post(() ->
                            Toast.makeText(mContext, R.string.error_generic, Toast.LENGTH_SHORT)
                                    .show());
                    e.printStackTrace();
                    DiagnosticLog.e(mContext, "DCC", () ->
                            diagnosticId() + " accepted download failed to start", e);
                    onDownloadDestroyed(this);
                    return;
                }

                synchronized (mDownloads) {
                    for (DownloadListener listener : mDownloadListeners)
                        listener.onDownloadUpdated(this);
                }
            });
        }

        public void reject() {
            if (!mPending || mCancelled)
                return;
            DiagnosticLog.i(mContext, "DCC", () -> diagnosticId() + " incoming offer rejected");
            onDownloadDestroyed(this);
        }

        public void cancel() {
            DiagnosticLog.i(mContext, "DCC", () ->
                    diagnosticId() + " download cancel requested pending=" + mPending);
            if (mPending) {
                onDownloadDestroyed(this);
            } else {
                synchronized (this) {
                    mCancelled = true;
                    if (mClient != null) {
                        mClient.close();
                        mClient = null;
                    }
                    if (mReverseClient != null) {
                        mReverseClient.close();
                        mReverseClient = null;
                    }
                }
            }
        }

        public AlertDialog createDownloadApprovalDialog(Context context,
                                                        ActivityDialogHandler handler) {
            String title;
            if (getFileSize() > 0)
                title = context.getString(R.string.dcc_approve_download_title_with_size,
                        getUnescapedFileName(), FormatUtils.formatByteSize(getFileSize()));
            else
                title = context.getString(R.string.dcc_approve_download_title,
                        getUnescapedFileName());
            DiagnosticLog.d(mContext, "DCC", () ->
                    diagnosticId() + " showing incoming offer approval dialog");
            AlertDialog ret = new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(context.getString(R.string.dcc_approve_download_body,
                            mSender.toString(), getServerName()))
                    .setPositiveButton(R.string.action_accept,
                            (DialogInterface dialog, int which) -> {
                                if (needsAskSystemDownloadsPermission())
                                    handler.askSystemDownloadsPermission(() -> approve());
                                else
                                    approve();
                            })
                    .setNegativeButton(R.string.action_reject,
                            (DialogInterface dialog, int which) -> reject())
                    .setOnCancelListener((DialogInterface dialog) -> reject())
                    .create();
            ret.setCanceledOnTouchOutside(false);
            return ret;
        }

    }

    private static class CancelledException extends IOException {
        public CancelledException() {
            super();
        }
    }

    public static class ActivityDialogHandler implements DownloadListener {

        private Activity mActivity;
        private AlertDialog mCurrentDialog;
        private ActivityResultLauncher<Intent> mDownloadsDirectoryLauncher;
        private List<Runnable> mStoragePermissionRequestCallbacks;
        private boolean mPermissionRequestPending;

        public ActivityDialogHandler(Activity activity) {
            mActivity = activity;
        }

        public void setDownloadsDirectoryLauncher(ActivityResultLauncher<Intent> launcher) {
            mDownloadsDirectoryLauncher = launcher;
        }

        public void onResume() {
            DCCManager.getInstance(mActivity).addDownloadListener(this);
            showDialogsIfNeeded();
        }

        public void onPause() {
            DCCManager.getInstance(mActivity).removeDownloadListener(this);
            if (mCurrentDialog != null) {
                mCurrentDialog.dismiss();
                mCurrentDialog = null;
            }
        }

        private void showDialog(AlertDialog dialog) {
            if (mCurrentDialog != null)
                mCurrentDialog.dismiss();
            mCurrentDialog = dialog;
            dialog.setOnDismissListener((DialogInterface i) -> {
                mCurrentDialog = null;
                showDialogsIfNeeded();
            });
            dialog.show();
        }

        private void showDialogsIfNeeded() {
            if (mCurrentDialog != null || mPermissionRequestPending)
                return;
            for (DownloadInfo download : DCCManager.getInstance(mActivity).getDownloads()) {
                if (download.isPending())
                    showDialog(download.createDownloadApprovalDialog(mActivity, this));
            }
        }

        @Override
        public void onDownloadCreated(DownloadInfo download) {
            if (download.isPending()) {
                mActivity.runOnUiThread(() -> {
                    if (mCurrentDialog == null && download.isPending()) // download is still pending
                        showDialog(download.createDownloadApprovalDialog(mActivity, this));
                });
            }
        }

        @Override
        public void onDownloadDestroyed(DownloadInfo download) {
        }

        @Override
        public void onDownloadUpdated(DownloadInfo download) {
        }


        public void handleDownloadsDirectoryResult(int resultCode, Intent data) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                DiagnosticLog.d(mActivity, "DCC", () ->
                        "Downloads directory picker returned a selection");
                try {
                    boolean read = (data.getFlags() &
                            Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
                    boolean write = (data.getFlags() &
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
                    if (read && write)
                        mActivity.getContentResolver().takePersistableUriPermission(
                                data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    else if (read)
                        mActivity.getContentResolver().takePersistableUriPermission(
                                data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    else if (write)
                        mActivity.getContentResolver().takePersistableUriPermission(
                                data.getData(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    final boolean readGranted = read;
                    final boolean writeGranted = write;
                    DiagnosticLog.d(mActivity, "DCC", () ->
                            "Persistable Downloads access processed read=" + readGranted +
                                    ", write=" + writeGranted);
                } catch (SecurityException e) {
                    Log.w("DCCManager", "Unable to persist Downloads access", e);
                    DiagnosticLog.w(mActivity, "DCC", () ->
                            "Unable to persist Downloads directory access", e);
                }
                DCCManager.getInstance(mActivity).setOverrideDownloadDirectory(
                        data.getData(), true);
                onSystemDownloadPermissionRequestFinished();
            } else {
                DiagnosticLog.d(mActivity, "DCC", () ->
                        "Downloads directory picker cancelled or returned no selection");
                showSystemDownloadsPermissionDenialDialog();
            }
        }

        public void askSystemDownloadsPermission(Runnable cb) {
            if (cb != null) {
                if (mStoragePermissionRequestCallbacks == null)
                    mStoragePermissionRequestCallbacks = new ArrayList<>();
                mStoragePermissionRequestCallbacks.add(cb);
            }
            mPermissionRequestPending = true;
            DiagnosticLog.i(mActivity, "DCC", () ->
                    "Requesting Downloads directory access callbackPending=" + (cb != null));
            if (mDownloadsDirectoryLauncher == null) {
                DiagnosticLog.w(mActivity, "DCC", () ->
                        "Downloads directory picker launcher unavailable", null);
                showSystemDownloadsPermissionDenialDialog();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            Uri downloads = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:" + Environment.DIRECTORY_DOWNLOADS);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloads);
            mDownloadsDirectoryLauncher.launch(intent);
        }

        private void onSystemDownloadPermissionRequestFinished() {
            mPermissionRequestPending = false;
            final int callbackCount = mStoragePermissionRequestCallbacks == null ? 0 :
                    mStoragePermissionRequestCallbacks.size();
            DiagnosticLog.d(mActivity, "DCC", () ->
                    "Downloads directory permission flow finished callbacks=" + callbackCount);
            if (mStoragePermissionRequestCallbacks != null) {
                DCCManager.getInstance(mActivity).checkSystemDownloadsDirectoryAccess();
                for (Runnable r : mStoragePermissionRequestCallbacks)
                    r.run();
                mStoragePermissionRequestCallbacks.clear();
            }
            showDialogsIfNeeded();
        }

        private void showSystemDownloadsPermissionDenialDialog() {
            DiagnosticLog.d(mActivity, "DCC", () ->
                    "Showing Downloads directory permission fallback dialog");
            new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.dcc_system_downloads_permission_dialog_title)
                    .setMessage(R.string.dcc_system_downloads_permission_dialog_text)
                    .setPositiveButton(R.string.action_ok, (DialogInterface i, int w) -> {
                        DCCManager.getInstance(mActivity).mPreferences.edit()
                                .putBoolean(PREF_DCC_ASKED_FOR_PERMISSION, true)
                                .apply();
                        DiagnosticLog.i(mActivity, "DCC", () ->
                                "User accepted Downloads permission fallback");
                        onSystemDownloadPermissionRequestFinished();
                    })
                    .setNegativeButton(R.string.action_ask_again, (DialogInterface i, int w) -> {
                        DiagnosticLog.i(mActivity, "DCC", () ->
                                "User requested Downloads directory picker again");
                        askSystemDownloadsPermission(null);
                    })
                    .show();
        }

    }

    private class ClientImpl extends DCCClientManager {

        private ServerConnectionInfo mServer;

        public ClientImpl(ServerConnectionInfo server) {
            mServer = server;
        }

        @Override
        public void onFileOffered(ServerConnectionData connection, MessagePrefix sender,
                                  String fileName, String address, int port, long fileSize) {
            String transferId = DiagnosticLog.pseudonym("dcc-download",
                    String.valueOf(mServer.getUUID()) + "|" +
                            (sender == null ? "" : sender.getNick()) + "|" +
                            String.valueOf(fileName));
            DiagnosticLog.i(mContext, "DCC", () ->
                    transferId + " incoming DCC SEND offer mode=direct sizeBytes=" + fileSize +
                            ", port=" + port + ", server=" + serverDiagnosticId(mServer));
            Log.d("DCCManager", "File offered: " + fileName +
                    " from " + address + ":" + port);
            onDownloadCreated(new DownloadInfo(mServer, sender, fileName, fileSize, address, port));
        }

        @Override
        public void onFileOfferedUsingReverse(ServerConnectionData connection, MessagePrefix sender,
                                              String fileName, long fileSize, int uploadId) {
            String transferId = DiagnosticLog.pseudonym("dcc-download",
                    String.valueOf(mServer.getUUID()) + "|" +
                            (sender == null ? "" : sender.getNick()) + "|" +
                            String.valueOf(fileName));
            DiagnosticLog.i(mContext, "DCC", () ->
                    transferId + " incoming DCC SEND offer mode=reverse sizeBytes=" + fileSize +
                            ", reverseId=" + uploadId + ", server=" + serverDiagnosticId(mServer));
            Log.d("DCCManager", "File offered: " + fileName + " (reverse)");
            onDownloadCreated(new DownloadInfo(mServer, sender, fileName, fileSize, uploadId));
        }
    }

    public interface DownloadListener {

        void onDownloadCreated(DownloadInfo download);

        void onDownloadDestroyed(DownloadInfo download);

        void onDownloadUpdated(DownloadInfo download);

    }



    public static String getLocalIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress())
                        continue;
                    String hostAddr = addr.getHostAddress();
                    if (hostAddr.indexOf(':') != -1) { // IPv6
                        continue;
                        /*
                        int iof = hostAddr.indexOf('%');
                        if (iof != -1)
                            hostAddr = hostAddr.substring(0, iof);
                        */
                    }
                    DiagnosticLog.d("DCC", () -> "Resolved local IPv4 address for reverse DCC");
                    return hostAddr;
                }
            }
        } catch (SocketException e) {
            DiagnosticLog.w("DCC", () -> "Resolving local IPv4 address failed", e);
        }
        DiagnosticLog.w("DCC", () -> "No local IPv4 address available for reverse DCC", null);
        return null;
    }


}

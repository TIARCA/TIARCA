package io.mrarm.irc;

import android.content.Context;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

import io.mrarm.chatlib.dto.ChannelList;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.IRCConnectionRequest;
import io.mrarm.chatlib.irc.cap.SASLCapability;
import io.mrarm.chatlib.irc.cap.SASLOptions;
import io.mrarm.irc.config.AppSettings;
import io.mrarm.irc.config.IdentitySettings;
import io.mrarm.irc.config.ServerConfigData;

/**
 * One-shot IRC connection used by the server editor to obtain LIST before a server is saved.
 *
 * This deliberately does not use ServerConnectionManager/ServerConnectionInfo: it must not create
 * a drawer entry, foreground service, reconnect state, notifications, SQLite chat storage or an
 * autoconnect/session-recovery entry. The connection is closed as soon as LIST is complete.
 */
public final class TemporaryChannelListConnection {

    public interface Callback {
        void onSuccess(List<ChannelList.Entry> entries);
        void onError(Exception error);
    }

    private final Context mContext;
    private final ServerConfigData mConfig;
    private final List<String> mAddresses;
    private IRCConnection mConnection;
    private Callback mCallback;
    private int mAddressIndex;
    private boolean mFinished;
    private boolean mCancelled;

    public TemporaryChannelListConnection(Context context, ServerConfigData config) {
        mContext = context.getApplicationContext();
        mConfig = config;
        mAddresses = new ArrayList<>(config.getConnectionAddresses());
    }

    public synchronized void start(Callback callback) {
        if (mCallback != null)
            throw new IllegalStateException("Temporary channel-list connection already started");
        mCallback = callback;
        mAddressIndex = 0;
        connectCurrentAddress();
    }

    public synchronized void cancel() {
        mCancelled = true;
        mFinished = true;
        closeCurrentConnection();
        mCallback = null;
    }

    private void connectCurrentAddress() {
        if (mCancelled || mFinished)
            return;
        if (mAddresses.isEmpty()) {
            finishError(new IllegalArgumentException("No IRC server address configured"));
            return;
        }

        final String address = mAddresses.get(mAddressIndex);
        IRCConnection connection = new IRCConnection();
        mConnection = connection;

        try {
            IRCConnectionRequest request = buildRequest(address);
            SASLOptions saslOptions = buildSaslOptions();
            if (saslOptions != null) {
                connection.getServerConnectionData().getCapabilityManager()
                        .registerCapability(new SASLCapability(saslOptions));
            }

            connection.connect(request, ignored -> {
                if (isInactive(connection))
                    return;
                connection.listChannels(list -> {
                    if (isInactive(connection))
                        return;
                    List<ChannelList.Entry> entries = list == null || list.getEntries() == null
                            ? new ArrayList<>() : new ArrayList<>(list.getEntries());
                    finishSuccess(entries);
                }, entry -> {
                    // The final callback above supplies the complete list. Keeping this callback
                    // non-null lets chatlib stream entries without touching the editor UI.
                }, error -> retryOrFail(connection, error));
            }, error -> retryOrFail(connection, error));
        } catch (Exception error) {
            retryOrFail(connection, error);
        }
    }

    private IRCConnectionRequest buildRequest(String address) throws GeneralSecurityException {
        IRCConnectionRequest request = new IRCConnectionRequest()
                .setServerAddress(address, mConfig.port);

        if (mConfig.charset != null && !mConfig.charset.trim().isEmpty())
            request.setCharset(Charset.forName(mConfig.charset));

        if (mConfig.nicks != null) {
            for (String nick : mConfig.nicks) {
                if (nick != null && !nick.trim().isEmpty())
                    request.addNick(nick.trim());
            }
        }
        if (request.getNickList() == null || request.getNickList().isEmpty()) {
            for (String nick : AppSettings.getDefaultNicks())
                request.addNick(nick);
        }
        if (request.getNickList() == null || request.getNickList().isEmpty())
            throw new IllegalArgumentException("No nickname configured");

        request.setUser(IdentitySettings.getUsername(mContext, mConfig));
        if (mConfig.realname != null && !mConfig.realname.trim().isEmpty())
            request.setRealName(mConfig.realname.trim());
        else if (AppSettings.getDefaultRealname() != null &&
                !AppSettings.getDefaultRealname().trim().isEmpty())
            request.setRealName(AppSettings.getDefaultRealname());
        else
            request.setRealName(request.getNickList().get(0));

        if (mConfig.pass != null && !mConfig.pass.isEmpty())
            request.setServerPass(mConfig.pass);

        if (mConfig.ssl) {
            KeyManager[] keyManagers = null;
            if (ServerConfigData.AUTH_SASL_EXTERNAL.equals(mConfig.authMode) &&
                    mConfig.getAuthCert() != null && mConfig.getAuthPrivateKey() != null) {
                keyManagers = new KeyManager[] {
                        new UserKeyManager(mConfig.getAuthCert(), mConfig.getAuthPrivateKey())
                };
            }
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, null, null);
            request.enableSSL(sslContext.getSocketFactory(),
                    HttpsURLConnection.getDefaultHostnameVerifier());
        }
        return request;
    }

    private SASLOptions buildSaslOptions() {
        if (ServerConfigData.AUTH_SASL.equals(mConfig.authMode) &&
                mConfig.authUser != null && !mConfig.authUser.isEmpty() &&
                mConfig.authPass != null && !mConfig.authPass.isEmpty()) {
            return SASLOptions.createPlainAuth(mConfig.authUser, mConfig.authPass);
        }
        if (ServerConfigData.AUTH_SASL_EXTERNAL.equals(mConfig.authMode))
            return SASLOptions.createExternal();
        return null;
    }

    private synchronized boolean isInactive(IRCConnection connection) {
        return mCancelled || mFinished || connection != mConnection;
    }

    private void retryOrFail(IRCConnection connection, Exception error) {
        synchronized (this) {
            if (isInactive(connection))
                return;
            closeCurrentConnection();
            if (mAddressIndex + 1 < mAddresses.size()) {
                mAddressIndex++;
                connectCurrentAddress();
                return;
            }
        }
        finishError(error);
    }

    private void finishSuccess(List<ChannelList.Entry> entries) {
        Callback callback;
        synchronized (this) {
            if (mCancelled || mFinished)
                return;
            mFinished = true;
            closeCurrentConnection();
            callback = mCallback;
            mCallback = null;
        }
        if (callback != null)
            callback.onSuccess(entries);
    }

    private void finishError(Exception error) {
        Callback callback;
        synchronized (this) {
            if (mCancelled || mFinished)
                return;
            mFinished = true;
            closeCurrentConnection();
            callback = mCallback;
            mCallback = null;
        }
        if (callback != null)
            callback.onError(error == null ? new Exception("Unable to retrieve channel list") : error);
    }

    private void closeCurrentConnection() {
        IRCConnection connection = mConnection;
        mConnection = null;
        if (connection != null) {
            try {
                connection.disconnect(true);
            } catch (RuntimeException ignored) {
            }
        }
    }
}

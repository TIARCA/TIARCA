package io.mrarm.irc;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.mrarm.irc.config.ClientCertificateManager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Requires a real Android runtime: Robolectric cannot exercise AndroidKeyStore and Conscrypt. */
@RunWith(AndroidJUnit4.class)
public class KeystoreClientCertificateInstrumentedTest {

    @Test
    public void clientCertificateWorksWithTls12() throws Exception {
        verifyMutualTls("TLSv1.2");
    }

    @Test
    public void clientCertificateWorksWithTls13() throws Exception {
        verifyMutualTls("TLSv1.3");
    }

    private void verifyMutualTls(String protocol) throws Exception {
        ClientCertificateManager.Identity identity =
                ClientCertificateManager.generateProtectedIdentity();
        try {
            X509Certificate cert = identity.certificate;
            PrivateKey privateKey = ClientCertificateManager.getPrivateKey(identity.alias);
            assertNotNull(cert);
            assertNotNull(privateKey);
            assertNull("Android Keystore must not export the private key", privateKey.getEncoded());
            cert.verify(cert.getPublicKey());
            cert.checkValidity();
            assertTrue(ClientCertificateManager.keyMatchesCertificate(cert, privateKey));

            // The same self-signed test certificate is used on both sides of loopback TLS.
            // Trust-all managers are restricted to this local instrumentation test.
            X509TrustManager localTestTrust = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            TrustManager[] trustManagers = new TrustManager[] { localTestTrust };

            UserKeyManager serverKey = new UserKeyManager(cert, privateKey) {
                @Override
                public String[] getServerAliases(String keyType, Principal[] issuers) {
                    return "RSA".equals(keyType) ? new String[] { "main" } : new String[0];
                }

                @Override
                public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                    return "RSA".equals(keyType) ? "main" : null;
                }
            };
            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(new KeyManager[] { serverKey }, trustManagers, null);
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(new KeyManager[] { new UserKeyManager(cert, privateKey) },
                    trustManagers, null);

            AtomicReference<Throwable> serverError = new AtomicReference<>();
            CountDownLatch finished = new CountDownLatch(1);
            try (SSLServerSocket listener = (SSLServerSocket) serverContext
                    .getServerSocketFactory().createServerSocket(
                            0, 1, InetAddress.getByName("127.0.0.1"))) {
                listener.setNeedClientAuth(true);
                listener.setEnabledProtocols(new String[] { protocol });
                Thread server = new Thread(() -> {
                    try (SSLSocket peer = (SSLSocket) listener.accept()) {
                        peer.setSoTimeout(15000);
                        peer.setEnabledProtocols(new String[] { protocol });
                        peer.startHandshake();
                        assertEquals(protocol, peer.getSession().getProtocol());
                        X509Certificate presented = (X509Certificate) peer.getSession()
                                .getPeerCertificates()[0];
                        assertArrayEquals(cert.getEncoded(), presented.getEncoded());
                    } catch (Throwable error) {
                        serverError.set(error);
                    } finally {
                        finished.countDown();
                    }
                }, "tiarca-keystore-tls-test");
                server.start();

                try (SSLSocket client = (SSLSocket) clientContext.getSocketFactory()
                        .createSocket("127.0.0.1", listener.getLocalPort())) {
                    client.setEnabledProtocols(new String[] { protocol });
                    client.setSoTimeout(15000);
                    client.startHandshake();
                    assertEquals(protocol, client.getSession().getProtocol());
                }

                assertTrue("Server handshake did not finish", finished.await(20, TimeUnit.SECONDS));
                if (serverError.get() != null)
                    throw new AssertionError("TLS server did not receive a valid client cert",
                            serverError.get());
            }
        } finally {
            ClientCertificateManager.deleteProtectedIdentity(identity.alias);
            assertNull(ClientCertificateManager.getPrivateKey(identity.alias));
        }
    }
}

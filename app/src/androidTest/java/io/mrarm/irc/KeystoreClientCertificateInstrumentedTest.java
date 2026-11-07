package io.mrarm.irc;

import android.os.Build;
import android.util.Base64;

import androidx.test.platform.app.InstrumentationRegistry;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.KeyStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.mrarm.irc.config.ClientCertificateManager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
        // TLS 1.3 is available from Android 10; Android 9 must exercise TLS 1.2 only.
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
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

            // The server uses a separate test-only PKCS#12 identity: AndroidKeyStore key
            // support for the SERVER role must not obscure the client-authentication test.
            // Trust-all managers are restricted to this loopback instrumentation test.
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

            KeyStore testServerKeys = KeyStore.getInstance("PKCS12");
            byte[] pkcs12 = readTestServerKeystore();
            testServerKeys.load(new ByteArrayInputStream(pkcs12), "test-only".toCharArray());
            KeyManagerFactory serverKeyManagers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            serverKeyManagers.init(testServerKeys, "test-only".toCharArray());

            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(serverKeyManagers.getKeyManagers(), trustManagers, null);
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

                Throwable clientError = null;
                try (SSLSocket client = (SSLSocket) clientContext.getSocketFactory()
                        .createSocket("127.0.0.1", listener.getLocalPort())) {
                    client.setEnabledProtocols(new String[] { protocol });
                    client.setSoTimeout(15000);
                    client.startHandshake();
                    assertEquals(protocol, client.getSession().getProtocol());
                } catch (Throwable error) {
                    clientError = error;
                }

                assertTrue("Server handshake did not finish", finished.await(20, TimeUnit.SECONDS));
                if (clientError != null || serverError.get() != null)
                    throw new AssertionError("TLS " + protocol +
                            " client error=" + clientError + ", server error=" + serverError.get(),
                            clientError != null ? clientError : serverError.get());
            }
        } finally {
            ClientCertificateManager.deleteProtectedIdentity(identity.alias);
            assertNull(ClientCertificateManager.getPrivateKey(identity.alias));
        }
    }
    private static byte[] readTestServerKeystore() throws Exception {
        // Stored only in the androidTest APK; never included in production APKs.
        try (InputStream in = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("tls-server-test.p12.base64");
             ByteArrayOutputStream encoded = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1)
                encoded.write(buffer, 0, count);
            return Base64.decode(encoded.toByteArray(), Base64.DEFAULT);
        }
    }
}

package io.mrarm.irc.config;

import com.google.gson.Gson;

import org.junit.Test;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClientCertificateManagerTest {

    @Test
    public void legacyExternalStillPresentsCertificate() {
        ServerConfigData legacy = new ServerConfigData();
        legacy.authMode = ServerConfigData.AUTH_SASL_EXTERNAL;
        assertTrue(legacy.shouldPresentClientCertificate());
        legacy.authMode = ServerConfigData.AUTH_SASL;
        assertFalse(legacy.shouldPresentClientCertificate());
    }

    @Test
    public void clientCertCanBePresentedWhileUsingPlainSasl() {
        ServerConfigData server = new ServerConfigData();
        server.authMode = ServerConfigData.AUTH_SASL;
        server.presentClientCertificate = true;
        server.authCertKeyStoreAlias = "io.tiarca.irc.client.test-alias";
        String serialized = new Gson().toJson(server);
        ServerConfigData restored = new Gson().fromJson(serialized, ServerConfigData.class);
        assertTrue(restored.shouldPresentClientCertificate());
        assertEquals(server.authCertKeyStoreAlias, restored.authCertKeyStoreAlias);
        assertNull(restored.authCertPrivKey);
    }

    @Test
    public void fingerprintMatchesKnownX509FixtureAndRejectsWrongPrivateKey() throws Exception {
        X509Certificate cert;
        try (InputStream input = getClass().getResourceAsStream(
                "/tls/self-signed-test-cert.pem")) {
            assertNotNull(input);
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
        assertEquals("ec62dc7e269c36b88c211ffd64b452e85064ad01",
                ClientCertificateManager.fingerprint(cert, "SHA-1"));
        assertEquals("d3b14a200171ef81229ea910b24e6e39ac0f6942d5540ce10152dec9a27557f0" +
                        "c60c296bd33f828b977c2f9efae1a7b86c8706ecc70f68f87290d9e89261e32a",
                ClientCertificateManager.fingerprint(cert, "SHA-512"));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair unrelated = generator.generateKeyPair();
        assertFalse(ClientCertificateManager.keyMatchesCertificate(cert, unrelated.getPrivate()));
    }
}

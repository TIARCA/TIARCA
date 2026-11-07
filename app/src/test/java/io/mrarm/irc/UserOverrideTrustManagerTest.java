package io.mrarm.irc;

import org.junit.Test;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;

public class UserOverrideTrustManagerTest {

    @Test
    public void testTemporaryCertificateTrust() throws Exception {
        X509Certificate cert;
        try (InputStream input = getClass().getResourceAsStream(
                "/tls/self-signed-test-cert.pem")) {
            assertNotNull(input);
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
        assertNotNull(cert);

        UserOverrideTrustManager trustManager = new UserOverrideTrustManager(null, UUID.randomUUID());
        trustManager.addCertificateException(cert, true);

        // Should not throw CertificateException because cert is temporarily trusted.
        trustManager.checkServerTrusted(new X509Certificate[] { cert }, "RSA");
    }
}

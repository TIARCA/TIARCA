package io.mrarm.irc;

import org.junit.Test;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;

public class UserOverrideTrustManagerTest {

    private X509Certificate generateSelfSignedCertificate() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair kp = keyPairGenerator.generateKeyPair();

        X500Name name = new X500Name("CN=Test Certificate");
        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date from = new Date(System.currentTimeMillis() - 10000);
        Date to = new Date(System.currentTimeMillis() + 86400000L);
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                name, serial, from, to, name,
                SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(holder.getEncoded()));
    }

    @Test
    public void testTemporaryCertificateTrust() throws Exception {
        X509Certificate cert = generateSelfSignedCertificate();
        assertNotNull(cert);

        UserOverrideTrustManager trustManager = new UserOverrideTrustManager(null, UUID.randomUUID());
        trustManager.addCertificateException(cert, true);

        // Should not throw CertificateException because cert is temporarily trusted
        trustManager.checkServerTrusted(new X509Certificate[] { cert }, "RSA");
    }
}

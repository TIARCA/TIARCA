package io.mrarm.irc.config;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

/**
 * Generates non-exportable client identities; imported/legacy PKCS#8 keys remain supported by
 * ServerConfigData. Never register a global crypto provider or rely on Android hidden APIs.
 */
public final class ClientCertificateManager {

    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS_PREFIX = "io.tiarca.irc.client.";
    private static final byte[] KEY_PAIR_TEST = "TIARCA client certificate key check"
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private ClientCertificateManager() {
    }

    public static final class Identity {
        public final String alias;
        public final X509Certificate certificate;

        private Identity(String alias, X509Certificate certificate) {
            this.alias = alias;
            this.certificate = certificate;
        }
    }

    public static Identity generateProtectedIdentity() throws GeneralSecurityException {
        String alias = ALIAS_PREFIX + UUID.randomUUID();
        long now = System.currentTimeMillis();
        Date from = new Date(now);
        Date to = new Date(now + 30L * 365L * 24L * 60L * 60L * 1000L);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                // TLS engines may apply the message digest themselves. Android explicitly
                // recommends DIGEST_NONE for private keys used in client authentication.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                // Android Conscrypt performs RSA-PSS padding in BoringSSL and requests
                // RSA/ECB/NoPadding from AndroidKeyStore for the already-padded data.
                // See https://github.com/google/conscrypt/issues/1201.
                // No new encryption/decryption purposes are authorized for this key.
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setCertificateSubject(new X500Principal("CN=TIARCA IRC Client Certificate"))
                .setCertificateSerialNumber(new BigInteger(64, new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(from)
                .setCertificateNotAfter(to)
                .build();

        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, STORE);
        generator.initialize(spec);

        try {
            generator.generateKeyPair();
            KeyStore store = openStore();
            X509Certificate certificate = (X509Certificate) store.getCertificate(alias);
            PrivateKey privateKey = getPrivateKey(alias);
            if (certificate == null || privateKey == null ||
                    !keyMatchesCertificate(certificate, privateKey))
                throw new GeneralSecurityException("Android Keystore certificate/key mismatch");
            certificate.checkValidity();
            certificate.verify(certificate.getPublicKey());
            return new Identity(alias, certificate);
        } catch (GeneralSecurityException e) {
            // Only delete this newly generated alias, not a key referenced by another server.
            try {
                deleteProtectedIdentity(alias);
            } catch (GeneralSecurityException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    public static void deleteProtectedIdentity(String alias) throws GeneralSecurityException {
        if (alias != null && alias.startsWith(ALIAS_PREFIX))
            openStore().deleteEntry(alias);
    }

    public static PrivateKey getPrivateKey(String alias) throws GeneralSecurityException {
        if (alias == null || !alias.startsWith(ALIAS_PREFIX))
            return null;
        Key key = openStore().getKey(alias, null);
        return key instanceof PrivateKey ? (PrivateKey) key : null;
    }

    public static boolean keyMatchesCertificate(X509Certificate certificate, PrivateKey privateKey)
            throws GeneralSecurityException {
        if (certificate == null || privateKey == null)
            return false;
        String algorithm = certificate.getPublicKey().getAlgorithm();
        if (!algorithm.equals(privateKey.getAlgorithm()))
            return false;
        String signatureAlgorithm;
        if ("RSA".equalsIgnoreCase(algorithm))
            signatureAlgorithm = "SHA256withRSA";
        else if ("EC".equalsIgnoreCase(algorithm))
            signatureAlgorithm = "SHA256withECDSA";
        else
            return false;

        Signature signer = Signature.getInstance(signatureAlgorithm);
        signer.initSign(privateKey);
        signer.update(KEY_PAIR_TEST);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(signatureAlgorithm);
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(KEY_PAIR_TEST);
        return verifier.verify(signature);
    }

    public static String fingerprint(X509Certificate certificate, String algorithm)
            throws GeneralSecurityException {
        byte[] hash = java.security.MessageDigest.getInstance(algorithm)
                .digest(certificate.getEncoded());
        final char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            out[i * 2] = hex[(hash[i] >>> 4) & 0xf];
            out[i * 2 + 1] = hex[hash[i] & 0xf];
        }
        return new String(out);
    }

    private static KeyStore openStore() throws GeneralSecurityException {
        KeyStore store = KeyStore.getInstance(STORE);
        try {
            store.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Android Keystore unavailable", e);
        }
        return store;
    }
}

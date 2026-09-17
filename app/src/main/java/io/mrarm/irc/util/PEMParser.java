package io.mrarm.irc.util;

import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

public class PEMParser {

    private static final String LINE_BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String LINE_END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

    private static final String LINE_BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String LINE_END_CERTIFICATE = "-----END CERTIFICATE-----";

    public static List<Object> parse(BufferedReader reader) throws IOException {
        DiagnosticLog.d("TLS", () -> "PEM parse started");
        List<Object> ret = new ArrayList<>();
        int certificateCount = 0;
        int keyCount = 0;
        int failureCount = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(LINE_BEGIN_CERTIFICATE)) {
                byte[] cert = readBase64Content(reader, LINE_END_CERTIFICATE);
                try {
                    CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    X509Certificate crt = (X509Certificate) factory.generateCertificate(
                            new ByteArrayInputStream(cert));
                    ret.add(crt);
                    certificateCount++;
                } catch (GeneralSecurityException e) {
                    Log.w("PEMParser", "Failed to load certificate");
                    e.printStackTrace();
                    failureCount++;
                    final int bytes = cert.length;
                    DiagnosticLog.w("TLS", () ->
                            "PEM certificate decode failed bytes=" + bytes, e);
                }
            }
            if (line.equals(LINE_BEGIN_PRIVATE_KEY)) {
                byte[] key = readBase64Content(reader, LINE_END_PRIVATE_KEY);
                try {
                    KeyFactory factory = KeyFactory.getInstance("RSA");
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
                    RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(keySpec);
                    ret.add(privateKey);
                    keyCount++;
                } catch (GeneralSecurityException e) {
                    Log.w("PEMParser", "Failed to load private key");
                    e.printStackTrace();
                    failureCount++;
                    final int bytes = key.length;
                    DiagnosticLog.w("TLS", () ->
                            "PEM private-key decode failed algorithm=RSA bytes=" + bytes, e);
                }
            }
        }
        final int certs = certificateCount;
        final int keys = keyCount;
        final int failures = failureCount;
        DiagnosticLog.i("TLS", () ->
                "PEM parse completed certificates=" + certs + ", privateKeys=" + keys +
                        ", failures=" + failures);
        return ret;
    }

    private static byte[] readBase64Content(BufferedReader reader, String expectedEnd)
            throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        boolean foundEnd = false;
        while ((line = reader.readLine()) != null) {
            if (line.equals(expectedEnd)) {
                foundEnd = true;
                break;
            }
            data.append(line);
        }
        final boolean terminatorFound = foundEnd;
        final int encodedChars = data.length();
        DiagnosticLog.d("TLS", () ->
                "PEM block read terminatorFound=" + terminatorFound +
                        ", encodedChars=" + encodedChars);
        return Base64.decode(data.toString(), Base64.DEFAULT);
    }

}

package io.mrarm.irc;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.chatlib.irc.IRCConnectionRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CleartextPolicyTest {

    @Test
    public void manifestDoesNotGloballyOptInToCleartextTraffic() throws Exception {
        String manifest = new String(
                Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8);
        assertFalse("Global cleartext opt-in must stay disabled",
                manifest.contains("usesCleartextTraffic=\"true\""));
    }

    @Test
    public void nonTlsIrcStillUsesRawTcpSocket() throws Exception {
        CountDownLatch receivedRegistration = new CountDownLatch(1);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket accepted = server.accept();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8))) {
                    String cap = reader.readLine();
                    String nick = reader.readLine();
                    String user = reader.readLine();

                    assertNotNull(cap);
                    assertNotNull(nick);
                    assertNotNull(user);
                    assertTrue(cap.startsWith("CAP LS 302"));
                    assertTrue(nick.startsWith("NICK cleartext-test"));
                    assertTrue(user.startsWith("USER cleartext-test "));
                    receivedRegistration.countDown();
                } catch (Throwable t) {
                    serverFailure.set(t);
                    receivedRegistration.countDown();
                }
            }, "cleartext-irc-test-server");
            serverThread.setDaemon(true);
            serverThread.start();

            IRCConnectionRequest request = new IRCConnectionRequest()
                    .setServerAddress("127.0.0.1", server.getLocalPort())
                    .disableSSL()
                    .setUser("cleartext-test")
                    .setUserMode(0)
                    .setRealName("TIARCA test")
                    .setNickList(Arrays.asList("cleartext-test"));

            assertFalse(request.isUsingSSL());

            IRCConnection connection = new IRCConnection();
            connection.connect(request, ignored -> { }, ignored -> { });

            assertTrue("Non-TLS IRC registration was not sent over raw TCP",
                    receivedRegistration.await(5, TimeUnit.SECONDS));
            connection.disconnect(false);

            if (serverFailure.get() != null)
                throw new AssertionError("Raw IRC socket test failed", serverFailure.get());
        }
    }
}

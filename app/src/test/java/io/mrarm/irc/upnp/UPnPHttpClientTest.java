package io.mrarm.irc.upnp;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UPnPHttpClientTest {

    @Test
    public void localHttpGetWorksWithoutGlobalCleartextOptIn() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            FutureTask<Void> responder = startResponder(server,
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 5\r\n" +
                            "Connection: close\r\n" +
                            "\r\n" +
                            "hello");

            URL url = new URL("http://127.0.0.1:" + server.getLocalPort() + "/rootDesc.xml");
            UPnPHttpClient.Response response = UPnPHttpClient.get(url);

            assertEquals(200, response.getStatusCode());
            assertEquals("hello",
                    new String(response.getBody(), StandardCharsets.UTF_8));
            responder.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void localHttpGetSupportsChunkedBodies() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            FutureTask<Void> responder = startResponder(server,
                    "HTTP/1.1 200 OK\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "Connection: close\r\n" +
                            "\r\n" +
                            "4\r\ntest\r\n" +
                            "3\r\ning\r\n" +
                            "0\r\n\r\n");

            URL url = new URL("http://127.0.0.1:" + server.getLocalPort() + "/service.xml");
            UPnPHttpClient.Response response = UPnPHttpClient.get(url);

            assertEquals("testing",
                    new String(response.getBody(), StandardCharsets.UTF_8));
            responder.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void publicCleartextEndpointIsRejected() throws Exception {
        try {
            UPnPHttpClient.resolveLocalAddress(new URL("http://8.8.8.8/rootDesc.xml"));
            fail("Expected non-local UPnP endpoint to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("non-local"));
        }
    }

    @Test
    public void defaultHttpPortIsResolved() throws Exception {
        assertEquals(80, UPnPHttpClient.getEffectivePort(
                new URL("http://127.0.0.1/rootDesc.xml")));
        assertEquals(443, UPnPHttpClient.getEffectivePort(
                new URL("https://127.0.0.1/rootDesc.xml")));
    }

    private static FutureTask<Void> startResponder(ServerSocket server, String response) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(1000);
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                int matched = 0;
                byte[] terminator = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
                while (matched < terminator.length) {
                    int value = in.read();
                    if (value < 0)
                        throw new IOException("Client closed before finishing HTTP request headers");
                    if (value == (terminator[matched] & 0xff))
                        matched++;
                    else
                        matched = value == (terminator[0] & 0xff) ? 1 : 0;
                }
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                out.write(response.getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            }
            return null;
        });
        Thread thread = new Thread(task, "UPnPHttpClientTest-responder");
        thread.start();
        return task;
    }
}

package io.mrarm.irc.upnp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small HTTP client used only for UPnP control traffic.
 *
 * Android blocks cleartext HttpURLConnection traffic when the app does not opt into cleartext
 * globally. TIARCA intentionally keeps that global opt-in disabled. UPnP IGD endpoints, however,
 * are normally exposed as HTTP services on the local gateway. For those local HTTP endpoints this
 * class uses a raw socket after verifying that the destination resolves to a local/private address.
 *
 * HTTPS endpoints still use HttpURLConnection normally.
 */
public final class UPnPHttpClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_HEADER_LINE_BYTES = 16 * 1024;
    private static final int MAX_HEADER_LINES = 128;
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private UPnPHttpClient() {
    }

    public static Response get(URL url) throws IOException {
        return execute(url, "GET", Collections.emptyMap(), null);
    }

    public static Response post(URL url, Map<String, String> headers, byte[] body)
            throws IOException {
        return execute(url, "POST", headers, body == null ? new byte[0] : body);
    }

    private static Response execute(URL url, String method, Map<String, String> headers, byte[] body)
            throws IOException {
        String protocol = url.getProtocol();
        if ("https".equalsIgnoreCase(protocol)) {
            resolveLocalAddress(url);
            return executeUsingUrlConnection(url, method, headers, body);
        }
        if (!"http".equalsIgnoreCase(protocol))
            throw new IOException("Unsupported UPnP URL scheme: " + protocol);
        return executeLocalHttp(url, method, headers, body);
    }

    private static Response executeUsingUrlConnection(URL url, String method,
                                                      Map<String, String> headers, byte[] body)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Connection", "close");
        for (Map.Entry<String, String> header : headers.entrySet())
            connection.setRequestProperty(header.getKey(), header.getValue());

        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            try (BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                out.write(body);
            }
        }

        try {
            int statusCode = connection.getResponseCode();
            InputStream input = statusCode >= 400
                    ? connection.getErrorStream() : connection.getInputStream();
            byte[] responseBody = input == null ? new byte[0] : readLimited(input, MAX_BODY_BYTES);
            return new Response(statusCode, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static Response executeLocalHttp(URL url, String method,
                                             Map<String, String> headers, byte[] body)
            throws IOException {
        InetAddress targetAddress = resolveLocalAddress(url);
        int port = getEffectivePort(url);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetAddress, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            String requestTarget = url.getFile();
            if (requestTarget == null || requestTarget.isEmpty())
                requestTarget = "/";

            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(requestTarget).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(formatHostHeader(url, port)).append("\r\n");
            request.append("Connection: close\r\n");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if ("Host".equalsIgnoreCase(header.getKey()) ||
                        "Connection".equalsIgnoreCase(header.getKey()) ||
                        "Content-Length".equalsIgnoreCase(header.getKey()))
                    continue;
                request.append(header.getKey()).append(": ").append(header.getValue())
                        .append("\r\n");
            }
            if (body != null)
                request.append("Content-Length: ").append(body.length).append("\r\n");
            request.append("\r\n");

            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null)
                out.write(body);
            out.flush();

            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            String statusLine = readLine(in);
            if (statusLine == null || !statusLine.startsWith("HTTP/"))
                throw new IOException("Invalid UPnP HTTP response status line");
            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length < 2)
                throw new IOException("Invalid UPnP HTTP response status line");

            final int statusCode;
            try {
                statusCode = Integer.parseInt(statusParts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid UPnP HTTP status code", e);
            }

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            for (int lineCount = 0; lineCount < MAX_HEADER_LINES; lineCount++) {
                String line = readLine(in);
                if (line == null)
                    throw new EOFException("Unexpected EOF in UPnP HTTP headers");
                if (line.isEmpty())
                    return new Response(statusCode, readResponseBody(in, responseHeaders));
                int separator = line.indexOf(':');
                if (separator <= 0)
                    continue;
                String name = line.substring(0, separator).trim().toLowerCase(Locale.US);
                String value = line.substring(separator + 1).trim();
                String previous = responseHeaders.get(name);
                responseHeaders.put(name, previous == null ? value : previous + "," + value);
            }
            throw new IOException("Too many UPnP HTTP headers");
        }
    }

    private static byte[] readResponseBody(InputStream in, Map<String, String> headers)
            throws IOException {
        String transferEncoding = headers.get("transfer-encoding");
        if (transferEncoding != null &&
                transferEncoding.toLowerCase(Locale.US).contains("chunked"))
            return readChunked(in);

        String contentLength = headers.get("content-length");
        if (contentLength != null) {
            final long length;
            try {
                length = Long.parseLong(contentLength.trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid UPnP HTTP Content-Length", e);
            }
            if (length < 0 || length > MAX_BODY_BYTES)
                throw new IOException("UPnP HTTP body is too large");
            return readExactly(in, (int) length);
        }

        return readLimited(in, MAX_BODY_BYTES);
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null)
                throw new EOFException("Unexpected EOF in UPnP chunked response");
            int extension = sizeLine.indexOf(';');
            String sizeValue = (extension == -1 ? sizeLine : sizeLine.substring(0, extension)).trim();

            final int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeValue, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid UPnP HTTP chunk size", e);
            }
            if (chunkSize < 0 || body.size() + (long) chunkSize > MAX_BODY_BYTES)
                throw new IOException("UPnP HTTP body is too large");

            if (chunkSize == 0) {
                while (true) {
                    String trailer = readLine(in);
                    if (trailer == null || trailer.isEmpty())
                        return body.toByteArray();
                }
            }

            byte[] chunk = readExactly(in, chunkSize);
            body.write(chunk);
            int cr = in.read();
            int lf = in.read();
            if (cr != '\r' || lf != '\n')
                throw new IOException("Invalid UPnP HTTP chunk terminator");
        }
    }

    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read < 0)
                throw new EOFException("Unexpected EOF in UPnP HTTP body");
            offset += read;
        }
        return data;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (body.size() + (long) read > maxBytes)
                    throw new IOException("UPnP HTTP body is too large");
                body.write(buffer, 0, read);
            }
            return body.toByteArray();
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= MAX_HEADER_LINE_BYTES) {
            int value = in.read();
            if (value == -1) {
                if (line.size() == 0)
                    return null;
                break;
            }
            if (value == '\n')
                break;
            line.write(value);
        }
        if (line.size() > MAX_HEADER_LINE_BYTES)
            throw new IOException("UPnP HTTP header line is too long");

        byte[] bytes = line.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r')
            length--;
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    static InetAddress resolveLocalAddress(URL url) throws IOException {
        if (url.getUserInfo() != null)
            throw new IOException("User info is not allowed in UPnP URLs");

        String host = url.getHost();
        if (host == null || host.isEmpty())
            throw new IOException("UPnP URL has no host");
        if (host.startsWith("[") && host.endsWith("]"))
            host = host.substring(1, host.length() - 1);

        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isAllowedLocalAddress(address))
                return address;
        }
        throw new IOException("Refusing non-local UPnP endpoint");
    }

    static int getEffectivePort(URL url) throws IOException {
        int port = url.getPort();
        if (port == -1)
            port = url.getDefaultPort();
        if (port <= 0 || port > 65535)
            throw new IOException("Invalid UPnP endpoint port");
        return port;
    }

    static boolean isAllowedLocalAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isMulticastAddress())
            return false;
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress())
            return true;

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // RFC 6598 shared address space, sometimes used by local ISP/router equipment.
            return first == 100 && second >= 64 && second <= 127;
        }
        if (bytes.length == 16) {
            // RFC 4193 unique-local IPv6 addresses (fc00::/7).
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private static String formatHostHeader(URL url, int effectivePort) {
        String host = url.getHost();
        if (host.indexOf(':') != -1 && !host.startsWith("["))
            host = "[" + host + "]";
        int defaultPort = url.getDefaultPort();
        return effectivePort == defaultPort ? host : host + ":" + effectivePort;
    }

    public static final class Response {
        private final int mStatusCode;
        private final byte[] mBody;

        Response(int statusCode, byte[] body) {
            mStatusCode = statusCode;
            mBody = body;
        }

        public int getStatusCode() {
            return mStatusCode;
        }

        public byte[] getBody() {
            return mBody;
        }

        public void requireSuccess() throws IOException {
            if (mStatusCode < 200 || mStatusCode >= 300)
                throw new IOException("UPnP HTTP request failed with status " + mStatusCode);
        }
    }
}

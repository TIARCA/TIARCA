package io.mrarm.irc.upnp;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.mrarm.irc.BuildConfig;

public class SSDPDiscovery {

    private static final InetSocketAddress BROADCAST_ADDR = new InetSocketAddress(
            "239.255.255.250", 1900);

    public static String buildSearchRequest(String deviceType, int waitSeconds) {
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: " + waitSeconds +"\r\n" +
                "ST: " + deviceType + "\r\n" +
                "USER-AGENT: Android/" + Build.VERSION.RELEASE + " UPnP/1.1" +
                " RevolutionIRC/" + BuildConfig.VERSION_NAME + "\r\n" +
                "\r\n";
    }

    private DatagramSocket mDiscoverySocket;
    private final byte[] mReceiveBuffer = new byte[Short.MAX_VALUE];
    private final DatagramPacket mReceivePacket = new DatagramPacket(mReceiveBuffer,
            mReceiveBuffer.length);
    private long mReceiveTimeout = -1;

    public void bind() throws SocketException {
        mDiscoverySocket = new DatagramSocket(0);
    }

    public void close() {
        mDiscoverySocket.close();
    }

    public void sendSearch(String deviceType, int waitSeconds) throws IOException {
        byte[] request = buildSearchRequest(deviceType, waitSeconds).getBytes(StandardCharsets.UTF_8);
        mDiscoverySocket.send(new DatagramPacket(request, request.length, BROADCAST_ADDR));
    }

    public void setReceiveTimeout(int waitSeconds) {
        if (waitSeconds < 0)
            mReceiveTimeout = -1;
        else
            mReceiveTimeout = System.currentTimeMillis() + waitSeconds * 1000;
    }

    public Response receive() throws IOException {
        while (true) {
            int timeout = 0;
            if (mReceiveTimeout > 0) {
                long remaining = mReceiveTimeout - System.currentTimeMillis();
                if (remaining <= 0)
                    return null;
                timeout = Math.max((int) remaining, 1);
            }
            mDiscoverySocket.setSoTimeout(timeout);
            mReceivePacket.setLength(mReceiveBuffer.length);
            try {
                mDiscoverySocket.receive(mReceivePacket);
            } catch (SocketTimeoutException ignored) {
                return null;
            }

            String decoded = new String(mReceivePacket.getData(), mReceivePacket.getOffset(),
                    mReceivePacket.getLength(), StandardCharsets.UTF_8);
            if (!decoded.startsWith("HTTP/1.1 200 OK\r\n")) {
                Log.w("SSDP", "Ignoring invalid response");
                continue;
            }

            int i = 19;
            Map<String, String> headers = new HashMap<>();
            while (true) {
                int j = decoded.indexOf("\r\n", i);
                if (j == -1)
                    break;
                String ent = decoded.substring(i, j);
                int s = ent.indexOf(':');
                if (s != -1) {
                    headers.put(ent.substring(0, s).trim().toUpperCase(),
                            ent.substring(s + 1).trim());
                }
                i = j + 2;
            }
            return new Response(headers);
        }
    }

    public static class Response {

        private Map<String, String> mHeaders;

        public Response(Map<String, String> headers) {
            mHeaders = headers;
        }

        public String get(String header) {
            return mHeaders.get(header);
        }

        public String getDescriptionLocation() {
            return get("LOCATION");
        }

    }


}

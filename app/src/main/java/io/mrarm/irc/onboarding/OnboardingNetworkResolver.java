package io.mrarm.irc.onboarding;

import java.util.List;
import java.util.Locale;

import io.mrarm.irc.config.ServerConfigData;

/** Matching rules for networks created or updated by the onboarding wizard. */
final class OnboardingNetworkResolver {

    private static final String SIMOSNAP_DEPRECATED_ADDRESS = "irc.simosnap.org";
    private static final String SIMOSNAP_OFFICIAL_ADDRESS = "irc.simosnap.com";

    static final class Resolution {
        final ServerConfigData existing;
        final String name;

        Resolution(ServerConfigData existing, String name) {
            this.existing = existing;
            this.name = name;
        }
    }

    private OnboardingNetworkResolver() {
    }

    static Resolution resolve(List<ServerConfigData> servers, String requestedName,
                              List<String> requestedAddresses) {
        String cleanName = clean(requestedName);
        for (ServerConfigData server : servers) {
            if (sameName(server.name, cleanName) &&
                    sameServer(server.getConnectionAddresses(), requestedAddresses)) {
                return new Resolution(server, server.name);
            }
        }

        String newName = cleanName;
        if (hasName(servers, newName)) {
            String host = primaryAddress(requestedAddresses);
            if (!host.isEmpty())
                newName = uniqueName(servers, host);
            else
                newName = uniqueName(servers, newName);
        }
        return new Resolution(null, newName);
    }

    static boolean sameServer(List<String> left, List<String> right) {
        if (left == null || right == null)
            return false;
        for (String a : left) {
            String normalizedA = normalizeHost(a);
            if (normalizedA.isEmpty())
                continue;
            for (String b : right) {
                if (normalizedA.equals(normalizeHost(b)))
                    return true;
            }
        }
        return false;
    }

    private static boolean sameName(String left, String right) {
        return clean(left).equalsIgnoreCase(clean(right));
    }

    private static boolean hasName(List<ServerConfigData> servers, String name) {
        for (ServerConfigData server : servers) {
            if (sameName(server.name, name))
                return true;
        }
        return false;
    }

    private static String uniqueName(List<ServerConfigData> servers, String base) {
        String cleanBase = clean(base);
        if (!hasName(servers, cleanBase))
            return cleanBase;
        int suffix = 2;
        while (hasName(servers, cleanBase + " " + suffix))
            suffix++;
        return cleanBase + " " + suffix;
    }

    private static String primaryAddress(List<String> addresses) {
        if (addresses == null)
            return "";
        for (String address : addresses) {
            String clean = clean(address);
            if (!clean.isEmpty())
                return clean;
        }
        return "";
    }

    private static String normalizeHost(String value) {
        String host = clean(value).toLowerCase(Locale.ROOT);
        if (SIMOSNAP_DEPRECATED_ADDRESS.equals(host))
            return SIMOSNAP_OFFICIAL_ADDRESS;
        return host;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

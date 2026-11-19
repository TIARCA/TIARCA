package io.mrarm.irc.lua;

import java.util.Locale;

/**
 * Conservative IRC nick!ident@host mask matcher for already-observed identities.
 * This does not resolve extbans, missing hosts, or identify a user by IP.
 */
final class LuaBanMaskMatcher {
    private LuaBanMaskMatcher() { }

    static boolean mayIdentify(String mask) {
        if (mask == null || mask.length() > 512 || mask.indexOf('!') < 0 ||
                mask.indexOf('@') < 0 || mask.indexOf(':') >= 0)
            return false;
        // A blanket ban contains no identifying information.
        return !("*!*@*".equals(mask) || "*!*@?".equals(mask));
    }

    static String exactNick(String mask) {
        if (!mayIdentify(mask)) return null;
        String nick = mask.substring(0, mask.indexOf('!'));
        if (nick.isEmpty() || nick.length() > 64 ||
                nick.indexOf('*') >= 0 || nick.indexOf('?') >= 0 ||
                nick.indexOf(' ') >= 0) return null;
        return nick;
    }

    static boolean matches(String mask, String nick, String ident, String host) {
        if (!mayIdentify(mask) || nick == null || ident == null || host == null ||
                ident.isEmpty() || host.isEmpty()) return false;
        String candidate = nick + "!" + ident + "@" + host;
        return glob(mask.toLowerCase(Locale.ROOT), candidate.toLowerCase(Locale.ROOT));
    }

    private static boolean glob(String mask, String value) {
        // Linear-space wildcard matching, without regex injection or backtracking blowup.
        int p = 0, v = 0, star = -1, from = -1;
        while (v < value.length()) {
            if (p < mask.length() &&
                    (mask.charAt(p) == '?' || mask.charAt(p) == value.charAt(v))) {
                p++;
                v++;
            } else if (p < mask.length() && mask.charAt(p) == '*') {
                star = p++;
                from = v;
            } else if (star >= 0) {
                p = star + 1;
                v = ++from;
            } else return false;
        }
        while (p < mask.length() && mask.charAt(p) == '*') p++;
        return p == mask.length();
    }
}

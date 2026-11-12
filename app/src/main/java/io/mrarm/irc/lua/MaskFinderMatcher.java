package io.mrarm.irc.lua;

import java.util.Locale;

/** Pure, bounded full-mask matching for the explicit /lua mask command. */
public final class MaskFinderMatcher {
    private MaskFinderMatcher() { }

    public static boolean validQuery(String query) {
        if (query == null || query.isEmpty() || query.length() > 192) return false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c <= 32 || c == 127 || c == '\\') return false;
        }
        return true;
    }

    public static boolean matches(String query, String fullMask) {
        if (!validQuery(query) || fullMask == null) return false;
        String mask = fullMask.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        // A complete IRC mask is anchored. A short search scans the entire
        // nick!ident@host, including both ends, even when it contains wildcards.
        boolean full = q.indexOf('!') >= 0 || q.indexOf('@') >= 0;
        if (!full && q.indexOf('*') < 0 && q.indexOf('?') < 0)
            return mask.contains(q);
        return globMatches(full ? q : "*" + q + "*", mask);
    }

    /** Linear wildcard matching: no regex backtracking on hostile wide masks. */
    private static boolean globMatches(String pattern, String value) {
        int p = 0, v = 0, star = -1, following = 0;
        while (v < value.length()) {
            if (p < pattern.length() &&
                    (pattern.charAt(p) == '?' || pattern.charAt(p) == value.charAt(v))) {
                p++;
                v++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                following = v;
            } else if (star >= 0) {
                p = star + 1;
                v = ++following;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }
}

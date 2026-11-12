package io.mrarm.irc.lua;

import java.util.Locale;
import java.util.regex.Pattern;

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
        StringBuilder regex = new StringBuilder();
        if (!full) regex.append(".*");
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (c == '*') regex.append(".*");
            else if (c == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        if (!full) regex.append(".*");
        return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(mask).matches();
    }
}

package io.mrarm.chatlib.irc;

/** IRC nickname comparison rules advertised through ISUPPORT CASEMAPPING. */
public enum IRCCaseMapping {
    ASCII,
    RFC1459,
    STRICT_RFC1459;

    public boolean equals(String first, String second) {
        if (first == null || second == null)
            return first == second;
        if (first.length() != second.length())
            return false;
        for (int i = 0; i < first.length(); i++) {
            if (fold(first.charAt(i)) != fold(second.charAt(i)))
                return false;
        }
        return true;
    }

    /** Returns whether {@code value} contains {@code query} using this IRC case mapping. */
    public boolean contains(String value, String query) {
        if (value == null || query == null)
            return false;
        if (query.isEmpty())
            return true;
        if (query.length() > value.length())
            return false;
        for (int start = 0; start <= value.length() - query.length(); start++) {
            int index = 0;
            while (index < query.length() &&
                    fold(value.charAt(start + index)) == fold(query.charAt(index))) {
                index++;
            }
            if (index == query.length())
                return true;
        }
        return false;
    }

    private char fold(char character) {
        if (character >= 'A' && character <= 'Z')
            character = (char) (character + ('a' - 'A'));
        if (this == ASCII)
            return character;
        if (character == '[') return '{';
        if (character == ']') return '}';
        if (character == '\\') return '|';
        if (this == RFC1459 && character == '^') return '~';
        return character;
    }

    public static IRCCaseMapping fromISupportValue(String value) {
        if (value == null)
            return RFC1459;
        if ("ascii".equalsIgnoreCase(value))
            return ASCII;
        if ("strict-rfc1459".equalsIgnoreCase(value))
            return STRICT_RFC1459;
        return RFC1459;
    }
}

package io.mrarm.irc.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parser kept separate from Android/network code so it can be unit-tested. */
public final class WiktionaryDefinitionParser {

    private WiktionaryDefinitionParser() { }

    public static String fromHtml(String html) {
        if (html == null) return null;
        Matcher list = Pattern.compile("<ol[^>]*>\\s*<li[^>]*>(.*?)(?:<ul|<ol|</li>)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!list.find()) return null;
        String value = list.group(1)
                .replaceAll("<sup[^>]*>.*?</sup>", " ")
                .replaceAll("<[^>]+>", " ");
        return clean(decodeEntities(value));
    }

    public static String fromWikitext(String wikitext) {
        Matcher line = Pattern.compile("(?m)^#(?![#:*])\\s*(.+)$").matcher(wikitext);
        if (!line.find()) return null;
        String value = line.group(1);
        value = value.replaceAll("\\[\\[([^]|]+)\\|([^]]+)]]", "$2")
                .replaceAll("\\[\\[([^]]+)]]", "$1")
                .replaceAll("\\{\\{(?:Term|Linkp)\\|([^|}]+)(?:\\|[^}]*)?}}", "$1")
                .replaceAll("\\{\\{Pn(?:\\|[^}]*)?}}", "")
                .replaceAll("\\{\\{[^}]+}}", "")
                .replace("''", "");
        return clean(decodeEntities(value));
    }

    private static String decodeEntities(String value) {
        value = value.replace("&nbsp;", " ").replace("&#160;", " ")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        Matcher numeric = Pattern.compile("&#(x?[0-9A-Fa-f]+);").matcher(value);
        StringBuffer result = new StringBuffer();
        while (numeric.find()) {
            String number = numeric.group(1);
            int radix = number.startsWith("x") ? 16 : 10;
            if (radix == 16) number = number.substring(1);
            String replacement;
            try { replacement = new String(Character.toChars(Integer.parseInt(number, radix))); }
            catch (Exception e) { replacement = numeric.group(); }
            numeric.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        numeric.appendTail(result);
        return result.toString();
    }

    private static String clean(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ")
                .replaceAll("\\(\\s+", "(").replaceAll("\\s+\\)", ")").trim();
    }
}

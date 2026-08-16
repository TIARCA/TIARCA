package io.mrarm.irc.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuickCommandHelperTest {

    @Test
    public void extractsRenderedWiktionaryDefinition() {
        String html = "<h3>Sostantivo</h3><ol><li><small>(<i>psicologia</i>)</small> " +
                "capacità di <a href='/wiki/reagire'>reagire</a> alle difficoltà" +
                "<ul><li>un esempio da non includere</li></ul></li></ol>";
        assertEquals("(psicologia) capacità di reagire alle difficoltà",
                WiktionaryDefinitionParser.fromHtml(html));
    }

    @Test
    public void extractsWikitextFallbackDefinition() {
        String source = "== {{-it-}} ==\n# {{Term|fisica|it}} [[capacità]] di un [[materiale]]";
        assertEquals("fisica capacità di un materiale",
                WiktionaryDefinitionParser.fromWikitext(source));
    }
}

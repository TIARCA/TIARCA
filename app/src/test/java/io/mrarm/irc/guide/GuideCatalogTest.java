package io.mrarm.irc.guide;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GuideCatalogTest {
    private List<GuideCatalog.Entry> read(String asset) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(asset);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int length;
            while ((length = input.read(bytes)) != -1)
                buffer.write(bytes, 0, length);
            return GuideCatalog.parse(buffer.toString(StandardCharsets.UTF_8.name()));
        }
    }

    @Test public void bothCatalogsHaveMatchingIdentifiersAndCategories() throws Exception {
        List<GuideCatalog.Entry> italian = read("guide_it.json");
        List<GuideCatalog.Entry> english = read("guide_en.json");
        assertTrue(italian.size() >= 45);
        assertEquals(english.size(), italian.size());
        for (int i = 0; i < italian.size(); i++) {
            assertEquals(english.get(i).id, italian.get(i).id);
            assertEquals(english.get(i).category, italian.get(i).category);
            assertEquals(english.get(i).syntax, italian.get(i).syntax);
            assertFalse(italian.get(i).source.isEmpty());
            assertFalse(english.get(i).source.isEmpty());
        }
    }

    @Test public void searchFindsSyntaxAndMatchesAccents() throws Exception {
        List<GuideCatalog.Entry> entries = read("guide_it.json");
        assertFalse(GuideCatalog.filter(entries, "advanced", "mode").isEmpty());
        assertFalse(GuideCatalog.filter(entries, "tiarca", "menzioni").isEmpty());
        assertFalse(GuideCatalog.filter(entries, "", "autenticazione").isEmpty());
        assertTrue(GuideCatalog.filter(entries, "basic", "testo-impossibile-123").isEmpty());
        assertFalse(GuideCatalog.filter(entries, "basic", "/whois").isEmpty());
    }

    @Test(expected = JSONException.class)
    public void duplicateIdsAreRejected() throws Exception {
        GuideCatalog.parse("{\"entries\":["
                + "{\"id\":\"a\",\"category\":\"basic\",\"title\":\"A\",\"summary\":\"x\",\"body\":\"x\"},"
                + "{\"id\":\"a\",\"category\":\"basic\",\"title\":\"B\",\"summary\":\"y\",\"body\":\"y\"}]}");
    }
}

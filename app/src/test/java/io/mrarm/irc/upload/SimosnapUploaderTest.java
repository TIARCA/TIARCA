package io.mrarm.irc.upload;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimosnapUploaderTest {
    @Test
    public void acceptsOnlyJwtShapedTokens() {
        assertTrue(SimosnapUploader.isJwtFormat("eyJhbGciOiJIUzI1NiJ9.eyJhY2NvdW50IjoiYSJ9.Abc_123-"));
        for (String token : new String[] {
                null, "", "*", "anonymous", "header.payload", "header.payload.",
                "header.payload.signature.extra", "a.b. c", "a.b.c\\n", "a.b.c "
        }) {
            assertFalse("Accepted malformed token: " + token,
                    SimosnapUploader.isJwtFormat(token));
        }
    }

    @Test
    public void rejectsOversizedJwt() {
        String over = "a".repeat(8190) + ".b.c";
        assertFalse(SimosnapUploader.isJwtFormat(over));
    }
}

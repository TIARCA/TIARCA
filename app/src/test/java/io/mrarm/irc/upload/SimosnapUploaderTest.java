package io.mrarm.irc.upload;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SimosnapUploaderTest {
    @Test
    public void acceptsOnlyExpectedTempSendShareLinks() throws Exception {
        assertEquals("https://tempsend.com/abcdefgh",
                SimosnapUploader.validateTempSendLocation("https://tempsend.com/abcdefgh"));
    }

    @Test
    public void rejectsUnexpectedUploadRedirects() {
        String[] invalid = {
                null,
                "",
                "http://tempsend.com/abcdefgh",
                "https://other.example/abcdefgh",
                "https://tempsend.com.evil.example/abcdefgh",
                "https://user@tempsend.com/abcdefgh",
                "https://tempsend.com:8443/abcdefgh",
                "https://tempsend.com/abcdefgh?token=secret",
                "https://tempsend.com/abcdefgh#fragment",
                "https://tempsend.com/",
                "https://tempsend.com/not-a-token",
                "//tempsend.com/abcdefgh"
        };
        for (String location : invalid) {
            assertThrows("Unexpected accepted URL: " + location, IOException.class,
                    () -> SimosnapUploader.validateTempSendLocation(location));
        }
    }
}

package io.mrarm.irc;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class UserKeyManagerTest {

    @Test
    public void engineAndSocketClientAliasSelectionAreConsistent() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        UserKeyManager manager = new UserKeyManager(null, pair.getPrivate());

        String[] supportedTypes = new String[] { "EC", "RSA" };
        assertEquals("main", manager.chooseClientAlias(supportedTypes, null, null));
        assertEquals("main", manager.chooseEngineClientAlias(supportedTypes, null, null));
        assertSame(pair.getPrivate(), manager.getPrivateKey("main"));

        String[] unsupportedTypes = new String[] { "EC" };
        assertNull(manager.chooseClientAlias(unsupportedTypes, null, null));
        assertNull(manager.chooseEngineClientAlias(unsupportedTypes, null, null));
        assertNull(manager.chooseEngineServerAlias("RSA", null, null));
    }
}

package io.mrarm.irc.lua;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Multi-network approval is explicit; no other IRC server gains access. */
public class LuaMultiServerPolicyTest {
    private static final String SCRIPT = "0a14a509-49e4-4d79-8884-9504e76a252f";
    private static final String A = "2598f719-dfb6-4bad-a69c-ff78cecb9315";
    private static final String B = "22d1fa48-7d17-4cab-a34c-88d63cd9885a";
    private static final String UNAPPROVED = "6e9b0831-90e4-4619-a77a-64f6e468ac01";

    @Test public void twoApprovedNetworksReceiveActionsButUnselectedDoesNot() {
        LuaScriptStore.Script script = new LuaScriptStore.Script(
                SCRIPT, "shared.lua", "hash", A, false, false, "")
                .configured(Arrays.asList(A, B), true, true, true, true, true, "");
        assertEquals(Arrays.asList(A, B), script.serverIds);
        assertEquals(A, script.serverId); // legacy first-server field
        assertTrue(script.isEnabledOn(A));
        assertTrue(script.isEnabledOn(B));
        assertFalse(script.isEnabledOn(UNAPPROVED));
        for (String server : Arrays.asList(A, B)) {
            assertTrue(LuaActionPolicy.permitsSend(script, server, "#shared",
                    "message", "#shared", "hello"));
            assertTrue(LuaModerationPolicy.scoped(script, server, "#shared",
                    "command", "#shared", true));
            assertTrue(LuaModerationPolicy.scoped(script, server, "#shared",
                    "command", "#shared", false));
            assertFalse(LuaActionPolicy.permitsSend(script, server, "#shared",
                    "message", "#other", "hello"));
        }
        assertFalse(LuaActionPolicy.permitsSend(script, UNAPPROVED, "#shared",
                "message", "#shared", "hello"));
        assertFalse(LuaModerationPolicy.scoped(script, UNAPPROVED, "#shared",
                "command", "#shared", true));
        assertFalse(LuaModerationPolicy.scoped(script, UNAPPROVED, "#shared",
                "command", "#shared", false));
    }

    @Test public void disablingOrRevokingScopeImmediatelyBlocksEveryNetwork() {
        LuaScriptStore.Script script = new LuaScriptStore.Script(
                SCRIPT, "shared.lua", "hash", A, true, true, "")
                .configured(Arrays.asList(A, B), true, true, false, false, false, "");
        LuaScriptStore.Script disabled = script.configured(script.serverIds,
                false, false, false, false, false, "");
        assertEquals(Arrays.asList(A, B), disabled.serverIds);
        for (String server : Arrays.asList(A, B)) {
            assertFalse(disabled.isEnabledOn(server));
            assertFalse(LuaActionPolicy.permitsSend(disabled, server, "#shared",
                    "message", "#shared", "hello"));
        }

        LuaScriptStore.Script onlyB = script.configured(
                Collections.singletonList(B), true, true, false, false, false, "");
        assertFalse(onlyB.isEnabledOn(A));
        assertTrue(onlyB.isEnabledOn(B));
        assertFalse(LuaActionPolicy.permitsSend(onlyB, A, "#shared",
                "message", "#shared", "hello"));
        assertTrue(LuaActionPolicy.permitsSend(onlyB, B, "#shared",
                "message", "#shared", "hello"));
    }

    @Test public void maskFinderHonorsBothApprovedNetworksAndRevocation() {
        LuaScriptStore.Script script = new LuaScriptStore.Script(
                SCRIPT, "mask_finder.lua", "hash", A, false, false, "")
                .configured(Arrays.asList(A, B), true, false, true, false, false, "");
        assertEquals(A, script.serverId); // legacy value must not limit Mask Finder
        assertTrue(MaskFinderManager.approvedForNetwork(script, A));
        assertTrue(MaskFinderManager.approvedForNetwork(script, B));
        assertFalse(MaskFinderManager.approvedForNetwork(script, UNAPPROVED));
        assertFalse(MaskFinderManager.approvedForNetwork(null, A));

        LuaScriptStore.Script withoutUserInfo = script.configured(script.serverIds,
                true, false, false, false, false, "");
        assertFalse(MaskFinderManager.approvedForNetwork(withoutUserInfo, A));
        assertFalse(MaskFinderManager.approvedForNetwork(withoutUserInfo, B));

        LuaScriptStore.Script onlyB = script.configured(
                Collections.singletonList(B), true, false, true, false, false, "");
        assertFalse(MaskFinderManager.approvedForNetwork(onlyB, A));
        assertTrue(MaskFinderManager.approvedForNetwork(onlyB, B));

        LuaScriptStore.Script disabled = script.configured(script.serverIds,
                false, false, false, false, false, "");
        assertFalse(MaskFinderManager.approvedForNetwork(disabled, A));
        assertFalse(MaskFinderManager.approvedForNetwork(disabled, B));
    }
}

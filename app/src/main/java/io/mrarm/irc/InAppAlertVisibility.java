package io.mrarm.irc;

import java.util.UUID;

import io.mrarm.chatlib.irc.IRCCaseMapping;

/** Matches an incoming alert to the conversation actually selected on screen. */
public final class InAppAlertVisibility {

    private InAppAlertVisibility() {
    }

    public static boolean isConversationVisible(UUID displayedServer, String displayedTarget,
                                                UUID incomingServer, String incomingTarget,
                                                IRCCaseMapping caseMapping) {
        return displayedServer != null && displayedServer.equals(incomingServer) &&
                displayedTarget != null && incomingTarget != null &&
                caseMapping != null && caseMapping.equals(displayedTarget, incomingTarget);
    }
}

package io.mrarm.irc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;

import io.mrarm.chatlib.irc.IRCCaseMapping;

/**
 * Process-lifetime foreground alert queue. Keeps message text only in memory,
 * so private content is not copied into preferences, logs or backups.
 */
public final class InAppAlertQueue {

    public static final int MAX_ALERTS = 128;

    private final Deque<Alert> mItems = new ArrayDeque<>();

    public boolean add(Alert item) {
        if (mItems.size() >= MAX_ALERTS)
            return false;
        mItems.addLast(item);
        return true;
    }

    public Alert current() {
        return mItems.peekFirst();
    }

    public int waitingCount() {
        return Math.max(0, mItems.size() - 1);
    }

    public int size() {
        return mItems.size();
    }

    public boolean dismissCurrent() {
        return mItems.pollFirst() != null;
    }

    /** Viewing a PVT handles its queued alerts without dismissing any channel mentions. */
    public boolean dismissPrivateConversation(UUID serverUUID, String target,
                                               IRCCaseMapping caseMapping) {
        boolean changed = false;
        Iterator<Alert> iterator = mItems.iterator();
        while (iterator.hasNext()) {
            Alert alert = iterator.next();
            if (alert.privateMessage && InAppAlertVisibility.isConversationVisible(
                    serverUUID, target, alert.serverUUID, alert.target, caseMapping)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    public void clear() {
        mItems.clear();
    }

    /** Apply a preference change to alerts already waiting in memory. */
    public void removeDisabledTypes(boolean privateMessagesEnabled, boolean mentionsEnabled) {
        Iterator<Alert> iterator = mItems.iterator();
        while (iterator.hasNext()) {
            Alert alert = iterator.next();
            if (alert.privateMessage ? !privateMessagesEnabled : !mentionsEnabled)
                iterator.remove();
        }
    }

    public static final class Alert {
        public final UUID serverUUID;
        public final String target;
        public final String sender;
        public final String message;
        public final boolean privateMessage;
        /** Storage identifier used to navigate to the exact message and flash it. */
        public final String messageId;
        public final boolean mention;

        public Alert(UUID serverUUID, String target, String sender, String message,
                     boolean privateMessage, String messageId, boolean mention) {
            this.serverUUID = serverUUID;
            this.target = target;
            this.sender = sender;
            this.message = message;
            this.privateMessage = privateMessage;
            this.messageId = messageId;
            this.mention = mention;
        }
    }
}

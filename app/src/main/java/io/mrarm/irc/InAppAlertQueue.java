package io.mrarm.irc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

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

    public void clear() {
        mItems.clear();
    }

    public static final class Alert {
        public final UUID serverUUID;
        public final String target;
        public final String sender;
        public final String message;
        public final boolean privateMessage;

        public Alert(UUID serverUUID, String target, String sender, String message,
                     boolean privateMessage) {
            this.serverUUID = serverUUID;
            this.target = target;
            this.sender = sender;
            this.message = message;
            this.privateMessage = privateMessage;
        }
    }
}

package io.mrarm.chatlib.irc;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class CommandHandlerListTest {

    @Test
    public void disconnectCallbackCanRemoveHandlerWithoutConcurrentModification() {
        CommandHandlerList handlers = new CommandHandlerList();
        SelfRemovingHandler handler = new SelfRemovingHandler(handlers);
        handlers.registerHandler(handler);

        handlers.notifyDisconnected();

        assertTrue(handler.wasCalled);
    }

    private static class SelfRemovingHandler implements CommandDisconnectHandler {
        private final CommandHandlerList handlers;
        private boolean wasCalled;

        SelfRemovingHandler(CommandHandlerList handlers) {
            this.handlers = handlers;
        }

        @Override
        public Object[] getHandledCommands() {
            // Two entries ensure that mutating the backing map during iteration would fail.
            return new Object[] { "TEST_DISCONNECT_A", "TEST_DISCONNECT_B" };
        }

        @Override
        public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                           List<String> params, Map<String, String> tags) {
        }

        @Override
        public void onDisconnected() {
            wasCalled = true;
            handlers.unregisterHandler(this);
        }
    }
}

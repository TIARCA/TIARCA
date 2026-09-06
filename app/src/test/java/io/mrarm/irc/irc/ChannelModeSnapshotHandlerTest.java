package io.mrarm.irc.irc;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.mrarm.chatlib.irc.CommandHandlerList;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.handlers.ModeCommandHandler;

import static org.junit.Assert.assertEquals;

public class ChannelModeSnapshotHandlerTest {

    @Test
    public void testRegistrationAndNoCollision() {
        CommandHandlerList handlers = new CommandHandlerList();
        handlers.addDefaultHandlers();

        // ModeCommandHandler is already registered for MODE in default handlers.
        ModeCommandHandler delegate = handlers.getHandler(ModeCommandHandler.class);
        handlers.unregisterHandler(delegate);

        ChannelModeSnapshotHandler snapshotHandler = new ChannelModeSnapshotHandler(delegate);
        // Registering snapshotHandler must succeed without name collision
        handlers.registerHandler(snapshotHandler);

        assertEquals(snapshotHandler, handlers.getHandler(ChannelModeSnapshotHandler.class));
    }

    @Test
    public void testModeListenerCallbacksAndUnsubscribe() throws InvalidMessageException {
        ChannelModeSnapshotHandler snapshotHandler = new ChannelModeSnapshotHandler(null);

        List<String> receivedModes = new ArrayList<>();
        ChannelModeSnapshotHandler.ModeListener listener1 = (connection, sender, params) -> {
            if (params.size() >= 3)
                receivedModes.add(params.get(0) + " " + params.get(1) + " " + params.get(2));
        };

        snapshotHandler.addModeListener(listener1);

        // Handle MODE #channel +b *!*@badhost
        snapshotHandler.handle(null, new MessagePrefix("oper!op@host"), "MODE",
                Arrays.asList("#channel", "+b", "*!*@badhost"), Collections.emptyMap());

        assertEquals(1, receivedModes.size());
        assertEquals("#channel +b *!*@badhost", receivedModes.get(0));

        // Handle MODE #channel +e *!ident@*
        snapshotHandler.handle(null, new MessagePrefix("oper!op@host"), "MODE",
                Arrays.asList("#channel", "+e", "*!ident@*"), Collections.emptyMap());

        assertEquals(2, receivedModes.size());
        assertEquals("#channel +e *!ident@*", receivedModes.get(1));

        // Unsubscribe
        snapshotHandler.removeModeListener(listener1);

        // Handle another MODE message
        snapshotHandler.handle(null, new MessagePrefix("oper!op@host"), "MODE",
                Arrays.asList("#channel", "-b", "*!*@badhost"), Collections.emptyMap());

        // Count should remain 2 as listener was unsubscribed
        assertEquals(2, receivedModes.size());
    }

    @Test
    public void testMultipleListenersAndChannelFiltering() throws InvalidMessageException {
        ChannelModeSnapshotHandler snapshotHandler = new ChannelModeSnapshotHandler(null);

        List<String> chan1Events = new ArrayList<>();
        List<String> chan2Events = new ArrayList<>();

        ChannelModeSnapshotHandler.ModeListener listenerChan1 = (conn, sender, params) -> {
            if (params.size() > 0 && params.get(0).equalsIgnoreCase("#chan1"))
                chan1Events.add(params.get(1));
        };

        ChannelModeSnapshotHandler.ModeListener listenerChan2 = (conn, sender, params) -> {
            if (params.size() > 0 && params.get(0).equalsIgnoreCase("#chan2"))
                chan2Events.add(params.get(1));
        };

        snapshotHandler.addModeListener(listenerChan1);
        snapshotHandler.addModeListener(listenerChan2);

        // Send MODE for #chan1
        snapshotHandler.handle(null, null, "MODE", Arrays.asList("#chan1", "+b", "mask1"), Collections.emptyMap());

        // Send MODE for #chan2
        snapshotHandler.handle(null, null, "MODE", Arrays.asList("#chan2", "+e", "mask2"), Collections.emptyMap());

        assertEquals(1, chan1Events.size());
        assertEquals("+b", chan1Events.get(0));

        assertEquals(1, chan2Events.size());
        assertEquals("+e", chan2Events.get(0));

        // Cleanup
        snapshotHandler.removeModeListener(listenerChan1);
        snapshotHandler.removeModeListener(listenerChan2);
    }
}

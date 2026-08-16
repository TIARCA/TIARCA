package io.mrarm.chatlib.irc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.mrarm.chatlib.irc.handlers.*;

public class CommandHandlerList {

    private HashMap<String, CommandHandler> handlers = new HashMap<>();
    private final Object handlersLock = new Object();
    private static CommandHandlerList defaultHandlers;
    private ErrorCommandHandler errorCommandHandler = new ErrorCommandHandler();

    public void addDefaultHandlers() {
        synchronized (CommandHandlerList.class) {
            if (defaultHandlers == null) {
                defaultHandlers = new CommandHandlerList();
                defaultHandlers.registerHandler(new JoinCommandHandler());
                defaultHandlers.registerHandler(new PartCommandHandler());
                defaultHandlers.registerHandler(new QuitCommandHandler());
                defaultHandlers.registerHandler(new ModeCommandHandler());
                defaultHandlers.registerHandler(new WelcomeCommandHandler());
                defaultHandlers.registerHandler(new ISupportCommandHandler());
                defaultHandlers.registerHandler(new PingCommandHandler());
                defaultHandlers.registerHandler(new AwayCommandHandler());
                defaultHandlers.registerHandler(new TopicCommandHandler());
                defaultHandlers.registerHandler(new TopicWhoTimeCommandHandler());
                defaultHandlers.registerHandler(new KickCommandHandler());
            }
        }
        synchronized (handlersLock) {
            synchronized (defaultHandlers.handlersLock) {
                handlers.putAll(defaultHandlers.handlers);
            }
        }

        // per-connection handlers
        registerHandler(new MessageCommandHandler());
        registerHandler(new PongCommandHandler(errorCommandHandler));
        registerHandler(new NickCommandHandler(errorCommandHandler));
        registerHandler(new WhoisCommandHandler(errorCommandHandler));
        registerHandler(new NamesReplyCommandHandler());
        registerHandler(new MotdCommandHandler());
        registerHandler(new CapCommandHandler());
        registerHandler(new ListCommandHandler());
    }

    public CommandHandler getHandlerFor(String command) throws InvalidMessageException {
        synchronized (handlersLock) {
            if (!handlers.containsKey(command)) {
                if (errorCommandHandler.canHandle(command))
                    return errorCommandHandler;
                throw new InvalidMessageException("No such command found (" + command + ")");
            }
            return handlers.get(command);
        }
    }

    public void registerHandler(CommandHandler handler) {
        synchronized (handlersLock) {
            for (Object command : handler.getHandledCommands()) {
                String s = getCommandString(command);
                if (handlers.containsKey(s))
                    throw new RuntimeException("Handler registration name collision");
                handlers.put(getCommandString(s), handler);
            }
        }
    }

    public void unregisterHandler(CommandHandler handler) {
        synchronized (handlersLock) {
            for (Object command : handler.getHandledCommands()) {
                handlers.remove(getCommandString(command));
            }
        }
    }

    private String getCommandString(Object o) {
        if (o instanceof Integer)
            return String.format("%03d", (Integer) o);
        return o.toString();
    }

    public <T> T getHandler(Class<? extends T> cl) {
        synchronized (handlersLock) {
            for (CommandHandler handler : handlers.values()) {
                if (handler.getClass().equals(cl))
                    return (T) handler;
            }
            return null;
        }
    }

    public ErrorCommandHandler getErrorCommandHandler() {
        return errorCommandHandler;
    }

    public void notifyDisconnected() {
        final List<CommandHandler> snapshot;
        synchronized (handlersLock) {
            snapshot = new ArrayList<>(handlers.values());
        }
        for (CommandHandler handler : snapshot) {
            if (handler instanceof CommandDisconnectHandler)
                ((CommandDisconnectHandler) handler).onDisconnected();
        }
    }

}

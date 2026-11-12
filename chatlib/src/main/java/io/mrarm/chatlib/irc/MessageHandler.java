package io.mrarm.chatlib.irc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageHandler {

    private ServerConnectionData connection;
    private CommandHandlerList commandHandlerList;

    public MessageHandler(ServerConnectionData connection) {
        this.connection = connection;
        this.commandHandlerList = connection.getCommandHandlerList();
    }

    public void handleLine(String line) throws InvalidMessageException {
        if (line.length() == 0)
            return;
        Map<String, String> tags = new HashMap<>();
        if (line.charAt(0) == '@') {
            // ircv3 tags
            int tagsEndI = line.indexOf(' ');
            String[] rawTags = line.substring(1, tagsEndI).split(";");
            line = line.substring(tagsEndI + 1);
            int vi;
            for (String tag : rawTags) {
                vi = tag.indexOf('=');
                if (vi == -1)
                    tags.put(tag, null);
                else
                    tags.put(tag.substring(0, vi), unescapeMessageTag(tag.substring(vi + 1)));
            }
        }
        MessagePrefix prefix = null;
        int prefixEndI = -1;
        if (line.startsWith(":")) {
            prefixEndI = line.indexOf(' ');
            if (prefixEndI == -1)
                throw new InvalidMessageException();
            prefix = new MessagePrefix(line.substring(1, prefixEndI));
            if (prefix.getNick().equalsIgnoreCase(connection.getUserNick())) {
                connection.setUserExtraInfo(prefix.getUser(), prefix.getHost());
            }
        }
        int commandEndI = line.indexOf(' ', prefixEndI + 1);
        String command;
        List<String> params;
        if (commandEndI == -1) {
            command = line.substring(prefixEndI + 1);
            params = new ArrayList<>();
        } else {
            command = line.substring(prefixEndI + 1, commandEndI);
            String paramsRaw = line.substring(commandEndI + 1);
            params = parseParams(paramsRaw);
        }
        CommandHandler handler;
        try {
            handler = commandHandlerList.getHandlerFor(command);
        } catch (InvalidMessageException missing) {
            // WHO 352, WHOX 354 and end-of-WHO 315 may not have a registered
            // handler on this network. They are legitimate, passive numerics.
            // Do not register a competing 354 handler: Simosnap already has one.
            if ("352".equals(command) || "354".equals(command) || "315".equals(command)) {
                connection.getIdentityCache().noteNumeric(command, params);
                return;
            }
            throw missing;
        }
        handler.handle(connection, prefix, command, params, tags);
        // Observe only server-processed data. No script runs here or on the IRC
        // reader thread; identity disclosure is separately gated in the app.
        IrcIdentityCache identities = connection.getIdentityCache();
        identities.noteNumeric(command, params);
        if (prefix != null && prefix.getUser() != null && prefix.getHost() != null)
            identities.note(prefix.getNick(), prefix.getUser(), prefix.getHost(), null);
        if ("NICK".equals(command) && prefix != null && !params.isEmpty())
            identities.rename(prefix.getNick(), params.get(0));
        if ("QUIT".equals(command) && prefix != null)
            identities.forget(prefix.getNick());
    }

    private List<String> parseParams(String paramsRaw) {
        List<String> params = new ArrayList<>();
        int i = 0;
        while (true) {
            if (i < paramsRaw.length() && paramsRaw.charAt(i) == ':') {
                params.add(paramsRaw.substring(i + 1));
                break;
            }
            int j = paramsRaw.indexOf(' ', i);
            if (j == -1) {
                params.add(paramsRaw.substring(i));
                break;
            } else {
                params.add(paramsRaw.substring(i, j));
            }
            i = j + 1;
        }
        return params;
    }

    private String unescapeMessageTag(String text) {
        StringBuilder outpBuilder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                int cc = text.charAt(++i);
                switch (cc) {
                    case ':':
                        outpBuilder.append(';');
                        break;
                    case 's':
                        outpBuilder.append(' ');
                        break;
                    case '\\':
                        outpBuilder.append('\\');
                        break;
                    case 'r':
                        outpBuilder.append('\r');
                        break;
                    case 'n':
                        outpBuilder.append('\n');
                        break;
                    default:
                        --i;
                }
            } else {
                outpBuilder.append(c);
            }
        }
        return outpBuilder.toString();
    }

}

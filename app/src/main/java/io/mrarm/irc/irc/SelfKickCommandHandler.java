package io.mrarm.irc.irc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.dto.KickMessageInfo;
import io.mrarm.chatlib.dto.MessageSenderInfo;
import io.mrarm.chatlib.irc.ChannelData;
import io.mrarm.chatlib.irc.CommandHandler;
import io.mrarm.chatlib.irc.InvalidMessageException;
import io.mrarm.chatlib.irc.MessagePrefix;
import io.mrarm.chatlib.irc.ServerConnectionData;
import io.mrarm.chatlib.user.UserInfo;
import io.mrarm.chatlib.user.WritableUserInfoApi;

/** Fixes chatlib's self-KICK ordering and clears stale channel presences. */
public final class SelfKickCommandHandler implements CommandHandler {

    private final CommandHandler delegate;

    public SelfKickCommandHandler(CommandHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { "KICK" };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags)
            throws InvalidMessageException {
        String kicked = CommandHandler.getParamWithCheck(params, 1);
        if (!kicked.equalsIgnoreCase(connection.getUserNick())) {
            delegate.handle(connection, sender, command, params, tags);
            return;
        }

        if (sender == null)
            sender = new MessagePrefix(connection.getUserNick());
        String channel = CommandHandler.getParamWithCheck(params, 0);
        String reason = CommandHandler.getParamOrNull(params, 2);

        try {
            // Persist the KICK while ChannelData still exists. Upstream removes the channel first,
            // then fails to find it when it tries to append this message.
            UUID senderId = connection.getUserInfoApi().resolveUser(sender.getNick(),
                    sender.getUser(), sender.getHost(), null, null).get();
            MessageSenderInfo senderInfo = new MessageSenderInfo(sender.getNick(),
                    sender.getUser(), sender.getHost(), null, senderId);
            ChannelData channelData = connection.getJoinedChannelData(channel);
            channelData.addMessage(new KickMessageInfo.Builder(senderInfo, kicked, reason), tags);

            WritableUserInfoApi users = connection.getUserInfoApi();
            for (String nick : users.findUsers("", null, null).get()) {
                UserInfo info = users.getUser(nick, null, null, null, null).get();
                if (info != null && containsChannel(info, channel))
                    users.setUserChannelPresence(info.getUUID(), channel, false, null, null).get();
            }

            // Notify listeners only after the channel state and presences are coherent.
            connection.onChannelLeft(channel);
        } catch (NoSuchChannelException e) {
            // A duplicate/late KICK for an already removed channel is safe to ignore.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean containsChannel(UserInfo info, String channel) {
        for (String item : info.getChannels()) {
            if (item.equalsIgnoreCase(channel))
                return true;
        }
        return false;
    }
}

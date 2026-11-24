package io.mrarm.chatlib.irc.handlers;

import io.mrarm.chatlib.NoSuchChannelException;
import io.mrarm.chatlib.dto.ModeList;
import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class NamesReplyCommandHandler implements CommandDisconnectHandler {

    public static final int RPL_NAMREPLY = 353;
    public static final int RPL_ENDOFNAMES = 366;

    private final Map<String, List<ChannelData.Member>> channelNamesList = new HashMap<>();

    @Override
    public Object[] getHandledCommands() {
        return new Object[] { RPL_NAMREPLY, RPL_ENDOFNAMES };
    }

    @Override
    public void handle(ServerConnectionData connection, MessagePrefix sender, String command,
                       List<String> params, Map<String, String> tags) throws InvalidMessageException {
        int numeric = CommandHandler.toNumeric(command);
        if (numeric == RPL_NAMREPLY) {
            int paramId = 1;
            String channelName = CommandHandler.getParamWithCheck(params, paramId);
            // if the first argument is '=' or '*' or '@', skip it as it's the channel type which we don't really care
            // about for now
            if (channelName.length() == 1 && (channelName.charAt(0) == '=' || channelName.charAt(0) == '*' ||
                    channelName.charAt(0) == '@'))
                channelName = CommandHandler.getParamWithCheck(params, ++paramId);

            List<ChannelData.Member> list = channelNamesList.get(channelName);
            if (list == null) {
                list = new ArrayList<>();
                channelNamesList.put(channelName, list);
            }
            List<NickWithPrefix> nicksWithPrefixes = new ArrayList<>();
            List<String> uuidRequestList = new ArrayList<>();
            for (String rawNick : CommandHandler.getParamWithCheck(params, ++paramId).split(" ")) {
                NickWithPrefix nickWithPrefix = connection.getNickPrefixParser().parse(connection, rawNick);
                nicksWithPrefixes.add(nickWithPrefix);
                uuidRequestList.add(nickWithPrefix.getNick());
            }
            Map<String, UUID> uuidResponse;
            try {
                uuidResponse = connection.getUserInfoApi().resolveUsers(uuidRequestList, null, null).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to resolve user list", e);
            }
            for (NickWithPrefix nickWithPrefix : nicksWithPrefixes) {
                UUID uuid = uuidResponse.get(nickWithPrefix.getNick());
                if (uuid == null)
                    continue;
                char[] prefixModes = null;
                if (nickWithPrefix.getNickPrefixes() != null) {
                    prefixModes = new char[nickWithPrefix.getNickPrefixes().length()];
                    int i = 0;
                    ServerSupportList s = connection.getSupportList();
                    for (char c : nickWithPrefix.getNickPrefixes())
                        prefixModes[i++] = s.getSupportedNickPrefixModes().get(s.getSupportedNickPrefixes().find(c));
                }
                list.add(new ChannelData.Member(uuid, prefixModes != null ? new ModeList(String.valueOf(prefixModes))
                        : null, nickWithPrefix.getNickPrefixes()));
            }
        } else if (numeric == RPL_ENDOFNAMES) {
            String channelName = CommandHandler.getParamWithCheck(params, 1);
            try {
                connection.getJoinedChannelData(channelName).setMembers(channelNamesList.containsKey(channelName)
                        ? channelNamesList.get(channelName) : new ArrayList<>());
            } catch (NoSuchChannelException e) {
                throw new InvalidMessageException("Invalid channel name", e);
            }
            List<ChannelData.Member> completed = channelNamesList.remove(channelName);
            // Resolve the current nicknames from the same UUIDs as the IRC roster:
            // a NICK between 353 and 366 must not pin the obsolete nickname.
            List<UUID> ids = new ArrayList<>();
            if (completed != null)
                for (ChannelData.Member member : completed) ids.add(member.getUserUUID());
            try {
                Map<UUID, String> nicks = connection.getUserInfoApi()
                        .getUsersNicks(ids, null, null).get();
                if (nicks.size() == new HashSet<>(ids).size())
                    connection.getIdentityCache().replaceChannelMembers(channelName, nicks.values());
                else
                    connection.getIdentityCache().markMembershipIncomplete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                connection.getIdentityCache().markMembershipIncomplete();
            } catch (ExecutionException e) {
                connection.getIdentityCache().markMembershipIncomplete();
            }
        }
    }

    @Override
    public void onDisconnected() {
        channelNamesList.clear();
    }
}

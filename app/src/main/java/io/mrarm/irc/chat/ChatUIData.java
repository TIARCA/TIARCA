package io.mrarm.irc.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mrarm.chatlib.ChannelListListener;
import io.mrarm.chatlib.ChatApi;

public class ChatUIData {

    private final Map<String, ChannelUIData> mChannelUIData = new HashMap<>();

    public void attachToConnection(ChatApi connection) {
        connection.subscribeChannelList(new ChannelListListener() {
            @Override
            public void onChannelListChanged(List<String> list) {
            }

            @Override
            public void onChannelJoined(String s) {
            }

            @Override
            public void onChannelLeft(String s) {
                synchronized (ChatUIData.this) {
                    mChannelUIData.remove(s.toLowerCase());
                }
            }
        }, null, null);
    }

    public synchronized ChannelUIData getChannelData(String channel) {
        channel = channel != null ? channel.toLowerCase() : channel;
        return mChannelUIData.get(channel);
    }

    public synchronized ChannelUIData getOrCreateChannelData(String channel) {
        channel = channel != null ? channel.toLowerCase() : channel;
        ChannelUIData data = mChannelUIData.get(channel);
        if (data == null) {
            data = new ChannelUIData();
            mChannelUIData.put(channel, data);
        }
        return data;
    }

    /** Keeps drafts and sent-text history attached to a private query after a NICK change. */
    public synchronized void renameChannel(String oldChannel, String newChannel) {
        if (oldChannel == null || newChannel == null || oldChannel.equalsIgnoreCase(newChannel))
            return;
        ChannelUIData oldData = mChannelUIData.remove(oldChannel.toLowerCase());
        if (oldData == null)
            return;
        String newKey = newChannel.toLowerCase();
        if (!mChannelUIData.containsKey(newKey))
            mChannelUIData.put(newKey, oldData);
    }

}

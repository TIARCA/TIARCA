from pathlib import Path

path = Path('app/src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java')
text = path.read_text()

def replace_once(old, new, label):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {count}')
    text = text.replace(old, new, 1)

replace_once(
    '    private boolean mIsLoadingMore;\n',
    '    private boolean mIsLoadingMore;\n    private volatile int mMessageWindowGeneration;\n',
    'generation field')

replace_once(
    '                    Log.i(TAG, "Load more (older): " + mChannelName);\n'
    '                    mIsLoadingMore = true;\n'
    '                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,\n',
    '                    Log.i(TAG, "Load more (older): " + mChannelName);\n'
    '                    mIsLoadingMore = true;\n'
    '                    final int generation = mMessageWindowGeneration;\n'
    '                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,\n',
    'older generation capture')

replace_once(
    '                            (MessageList messages) -> {\n'
    '                                updateMessageList(() -> {\n'
    '                                    mAdapter.addMessagesToTop(messages.getMessages(),\n'
    '                                            messages.getMessageIds());\n',
    '                            (MessageList messages) -> {\n'
    '                                updateMessageList(() -> {\n'
    '                                    if (generation != mMessageWindowGeneration)\n'
    '                                        return;\n'
    '                                    mAdapter.addMessagesToTop(messages.getMessages(),\n'
    '                                            messages.getMessageIds());\n',
    'older stale callback guard')

replace_once(
    '                    Log.i(TAG, "Load more (newer): " + mChannelName);\n'
    '                    mIsLoadingMore = true;\n'
    '                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,\n',
    '                    Log.i(TAG, "Load more (newer): " + mChannelName);\n'
    '                    mIsLoadingMore = true;\n'
    '                    final int generation = mMessageWindowGeneration;\n'
    '                    mConnection.getApiInstance().getMessageStorageApi().getMessages(mChannelName,\n',
    'newer generation capture')

replace_once(
    '                            (MessageList messages) -> {\n'
    '                                updateMessageList(() -> {\n'
    '                                    mAdapter.addMessagesToBottom(messages.getMessages(),\n'
    '                                            messages.getMessageIds());\n',
    '                            (MessageList messages) -> {\n'
    '                                updateMessageList(() -> {\n'
    '                                    if (generation != mMessageWindowGeneration)\n'
    '                                        return;\n'
    '                                    mAdapter.addMessagesToBottom(messages.getMessages(),\n'
    '                                            messages.getMessageIds());\n',
    'newer stale callback guard')

replace_once(
    '        else\n'
    '            mMessageFilterOptions = null;\n'
    '        mUnreadCheckedFirst = -1;\n',
    '        else\n'
    '            mMessageFilterOptions = null;\n'
    '        final int generation = ++mMessageWindowGeneration;\n'
    '        // A full window reload supersedes any older/newer pagination still in flight.\n'
    '        mLoadOlderIdentifier = null;\n'
    '        mLoadNewerIdentifier = null;\n'
    '        mIsLoadingMore = false;\n'
    '        mUnreadCheckedFirst = -1;\n',
    'reload invalidation')

replace_once(
    '        ResponseCallback<MessageList> cb = (MessageList messages) -> {\n'
    '            Log.i(TAG, "Got message list for " + mChannelName + ": " +\n',
    '        ResponseCallback<MessageList> cb = (MessageList messages) -> {\n'
    '            if (generation != mMessageWindowGeneration)\n'
    '                return;\n'
    '            Log.i(TAG, "Got message list for " + mChannelName + ": " +\n',
    'reload callback guard')

replace_once(
    '            updateMessageList(() -> {\n'
    '                mAdapter.setMessages(messages.getMessages(), messages.getMessageIds());\n',
    '            updateMessageList(() -> {\n'
    '                if (generation != mMessageWindowGeneration)\n'
    '                    return;\n'
    '                mAdapter.setMessages(messages.getMessages(), messages.getMessageIds());\n',
    'reload main-thread guard')

replace_once(
    '                mLoadOlderIdentifier = messages.getOlder();\n'
    '            });\n',
    '                mLoadOlderIdentifier = messages.getOlder();\n'
    '                mLoadNewerIdentifier = nearMessage == null ? null : messages.getNewer();\n'
    '                mIsLoadingMore = false;\n'
    '            });\n',
    'reload paging state')

replace_once(
    '        if (nearMessage != null) {\n'
    '            storage.getMessagesNear(mChannelName, nearMessage,\n'
    '                    getFilterOptions(), (MessageList messages) -> {\n'
    '                        cb.onResponse(messages);\n'
    '                        updateMessageList(() -> {\n'
    '                            mLoadNewerIdentifier = messages.getNewer();\n'
    '                        });\n'
    '                    }, null);\n',
    '        if (nearMessage != null) {\n'
    '            storage.getMessagesNear(mChannelName, nearMessage,\n'
    '                    getFilterOptions(), cb, null);\n',
    'near-message callback consolidation')

path.write_text(text)

test = Path('app/src/test/java/io/mrarm/irc/JumpToLatestStructureTest.java')
test.write_text('''package io.mrarm.irc;\n\nimport org.junit.Test;\n\nimport java.io.IOException;\nimport java.nio.charset.StandardCharsets;\nimport java.nio.file.Files;\nimport java.nio.file.Path;\nimport java.nio.file.Paths;\n\nimport static org.junit.Assert.assertTrue;\n\n/** Regression coverage for TIARCA-025 jump-to-latest pagination invalidation. */\npublic class JumpToLatestStructureTest {\n\n    @Test\n    public void fullReloadInvalidatesStalePaginationWindow() throws IOException {\n        String source = readChatMessagesFragment();\n        assertTrue(source.contains("final int generation = ++mMessageWindowGeneration;"));\n        assertTrue(source.contains("mLoadOlderIdentifier = null;"));\n        assertTrue(source.contains("mLoadNewerIdentifier = null;"));\n        assertTrue(source.contains("generation != mMessageWindowGeneration"));\n        assertTrue(source.contains(\n                "mLoadNewerIdentifier = nearMessage == null ? null : messages.getNewer();"));\n    }\n\n    @Test\n    public void jumpToLatestStillUsesFreshLatestWindowWhenNewerHistoryExists()\n            throws IOException {\n        String source = readChatMessagesFragment();\n        assertTrue(source.contains(\n                "if (mLoadNewerIdentifier != null)\\n            reloadMessages(null);"));\n    }\n\n    private static String readChatMessagesFragment() throws IOException {\n        Path path = Paths.get(\n                "app/src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java");\n        if (!Files.exists(path))\n            path = Paths.get(\n                    "src/main/java/io/mrarm/irc/chat/ChatMessagesFragment.java");\n        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);\n    }\n}\n''')

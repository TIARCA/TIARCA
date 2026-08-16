package io.mrarm.irc.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import io.mrarm.irc.util.SpannableStringHelper;

public class ChannelUIData {

    private static final int HISTORY_MAX_COUNT = 24;

    private final List<CharSequence> mSentMessageHistory = new ArrayList<>();
    private final LinkedHashMap<String, String> mObservedNicks = new LinkedHashMap<>();
    private CharSequence mCurrentText = null;
    private long mLastMemberRefresh;

    private static final int MAX_OBSERVED_NICKS = 512;

    public void setCurrentText(CharSequence currentText) {
        if (currentText == null || currentText.length() == 0)
            mCurrentText = null;
        else
            mCurrentText = SpannableStringHelper.copyCharSequence(currentText);
    }

    public CharSequence getCurrentText() {
        return mCurrentText;
    }

    public List<CharSequence> getSentMessageHistory() {
        return mSentMessageHistory;
    }

    public void addHistoryMessage(CharSequence msg) {
        mSentMessageHistory.add(SpannableStringHelper.copyCharSequence(msg));
        if (mSentMessageHistory.size() >= HISTORY_MAX_COUNT)
            mSentMessageHistory.remove(0);
    }

    public synchronized boolean observeNick(String nick) {
        if (nick == null || nick.trim().isEmpty())
            return false;
        String key = nick.toLowerCase(Locale.ROOT);
        String previous = mObservedNicks.put(key, nick);
        if (mObservedNicks.size() > MAX_OBSERVED_NICKS) {
            String oldest = mObservedNicks.keySet().iterator().next();
            mObservedNicks.remove(oldest);
        }
        return previous == null || !previous.equals(nick);
    }

    public synchronized boolean forgetNick(String nick) {
        return nick != null && mObservedNicks.remove(nick.toLowerCase(Locale.ROOT)) != null;
    }

    public synchronized boolean renameNick(String oldNick, String newNick) {
        boolean changed = forgetNick(oldNick);
        return observeNick(newNick) || changed;
    }

    public synchronized List<String> getObservedNicks() {
        return new ArrayList<>(mObservedNicks.values());
    }

    public synchronized boolean shouldRefreshMembers(long now, long minimumIntervalMs) {
        if (now - mLastMemberRefresh < minimumIntervalMs)
            return false;
        mLastMemberRefresh = now;
        return true;
    }

}

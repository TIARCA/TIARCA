package io.mrarm.irc.chat;

import android.app.Dialog;
import android.graphics.Color;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.mrarm.chatlib.dto.NickWithPrefix;
import io.mrarm.chatlib.irc.IRCCaseMapping;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.dialog.UserBottomSheetDialog;
import io.mrarm.irc.dialog.NicknameContextMenu;
import io.mrarm.irc.util.IRCColorUtils;
import io.mrarm.irc.util.LinkHelper;
import io.mrarm.irc.util.SpannableStringHelper;
import io.mrarm.irc.util.StyledAttributesHelper;
import io.mrarm.irc.util.SimosnapAvatarLoader;
import io.mrarm.irc.util.SimosnapAvatarManager;
import io.mrarm.chatlib.user.UserInfo;

public class ChannelInfoAdapter extends RecyclerView.Adapter {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_TOPIC = 1;
    public static final int TYPE_MEMBER = 2;
    public static final int TYPE_SEARCH = 3;

    private static final int MEMBER_SEARCH_THRESHOLD = 30;

    private ServerConnectionInfo mConnection;
    private String mChannel;
    private String mTopic;
    private String mTopicSetBy;
    private Date mTopicSetOn;
    private List<NickWithPrefix> mMembers;
    private List<NickWithPrefix> mVisibleMembers = new ArrayList<>();
    private String mSearchQuery = "";
    private EditText mSearchInput;

    public ChannelInfoAdapter() {
    }

    public void setData(ServerConnectionInfo connection, String channel, String topic, String topicSetBy,
                        Date topicSetOn, List<NickWithPrefix> members) {
        boolean contextChanged = connection != mConnection || !TextUtils.equals(channel, mChannel);
        mConnection = connection;
        mChannel = channel;
        mTopic = topic;
        mTopicSetBy = topicSetBy;
        mTopicSetOn = topicSetOn;
        mMembers = members;
        boolean showMemberSearch = isMemberSearchVisible(getMemberCount());
        if (contextChanged || !showMemberSearch)
            mSearchQuery = "";
        if (!showMemberSearch)
            onDrawerClosed();
        rebuildVisibleMembers();
        SimosnapAvatarManager.requestChannelAccounts(connection, channel,
                this::notifyDataSetChanged);
        notifyDataSetChanged();
    }

    public List<NickWithPrefix> getMembers() {
        return mMembers;
    }

    public void onDrawerClosed() {
        if (mSearchInput == null)
            return;
        InputMethodManager inputMethodManager = (InputMethodManager) mSearchInput.getContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        mSearchInput.clearFocus();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_info_header, viewGroup, false);
            return new TextHolder(view);
        } else if (viewType == TYPE_TOPIC) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_topic, viewGroup, false);
            return new TopicHolder(view);
        } else if (viewType == TYPE_SEARCH) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_member_search, viewGroup, false);
            return new SearchHolder(view, this);
        } else { // TYPE_MEMBER
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.chat_member, viewGroup, false);
            return new MemberHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int type = holder.getItemViewType();
        if (type == TYPE_HEADER)
            ((TextHolder) holder).bind(position == 0 ? R.string.channel_topic
                    : R.string.channel_members);
        else if (type == TYPE_TOPIC)
            ((TopicHolder) holder).bind(mTopic, mTopicSetBy, mTopicSetOn);
        else if (type == TYPE_SEARCH)
            ((SearchHolder) holder).bind(mSearchQuery);
        else if (type == TYPE_MEMBER)
            ((MemberHolder) holder).bind(mConnection, mChannel,
                    mVisibleMembers.get(position - getMemberStart()));
    }

    @Override
    public int getItemCount() {
        return getMemberStart() + mVisibleMembers.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 || position == getMemberHeaderPosition())
            return TYPE_HEADER;
        if (position == 1)
            return TYPE_TOPIC;
        if (isMemberSearchVisible(getMemberCount()) && position == 2)
            return TYPE_SEARCH;
        return TYPE_MEMBER;
    }

    static boolean isMemberSearchVisible(int memberCount) {
        return memberCount >= MEMBER_SEARCH_THRESHOLD;
    }

    static boolean matchesMemberSearch(String nick, String query, IRCCaseMapping caseMapping) {
        return caseMapping != null && caseMapping.contains(nick, query == null ? "" : query);
    }

    private int getMemberCount() {
        return mMembers == null ? 0 : mMembers.size();
    }

    private int getMemberHeaderPosition() {
        return isMemberSearchVisible(getMemberCount()) ? 3 : 2;
    }

    private int getMemberStart() {
        return getMemberHeaderPosition() + 1;
    }

    private void setSearchQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (TextUtils.equals(mSearchQuery, normalized))
            return;
        mSearchQuery = normalized;
        rebuildVisibleMembers();
        notifyDataSetChanged();
    }

    private void rebuildVisibleMembers() {
        if (mMembers == null) {
            mVisibleMembers = new ArrayList<>();
            return;
        }
        if (mSearchQuery.isEmpty()) {
            mVisibleMembers = mMembers;
            return;
        }
        IRCCaseMapping caseMapping = IRCCaseMapping.RFC1459;
        if (mConnection != null && mConnection.getApiInstance() instanceof IRCConnection)
            caseMapping = ((IRCConnection) mConnection.getApiInstance()).getServerConnectionData()
                    .getSupportList().getCaseMapping();
        List<NickWithPrefix> filtered = new ArrayList<>();
        for (NickWithPrefix member : mMembers) {
            if (member != null && matchesMemberSearch(member.getNick(), mSearchQuery, caseMapping))
                filtered.add(member);
        }
        mVisibleMembers = filtered;
    }

    public static class TextHolder extends RecyclerView.ViewHolder {

        private TextView textView;

        public TextHolder(View view) {
            super(view);
            textView = (TextView) view;
        }

        public void bind(int title) {
            textView.setText(title);
        }

        public void bind(String title) {
            if (title != null) {
                textView.setText(title);
            } else {
                textView.setText(null);
            }
        }

    }

    public static class TopicHolder extends RecyclerView.ViewHolder {

        private TextView topicTextView;
        private TextView topicInfoTextView;
        private int textColorSecondary;

        public TopicHolder(View view) {
            super(view);
            topicTextView = view.findViewById(R.id.topic);
            topicInfoTextView = view.findViewById(R.id.topic_info);
            textColorSecondary = StyledAttributesHelper.getColor(topicTextView.getContext(),
                    android.R.attr.textColorSecondary, Color.BLACK);

            topicTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        public void bind(String topic, String topicSetBy, Date topicSetOn) {
            if (topic != null) {
                topicTextView.setText(LinkHelper.addLinks(IRCColorUtils.getFormattedString(
                        topicTextView.getContext(), topic)));
            } else {
                SpannableString noTopicColored = new SpannableString(topicTextView.getResources()
                        .getString(R.string.channel_topic_none));
                noTopicColored.setSpan(new ForegroundColorSpan(textColorSecondary), 0,
                        noTopicColored.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                topicTextView.setText(noTopicColored);
            }
            if (topicSetBy != null && topicSetOn != null) {
                SpannableString topicSetByColored = new SpannableString(topicSetBy);
                topicSetByColored.setSpan(new ForegroundColorSpan(IRCColorUtils.getNickColor(
                        topicInfoTextView.getContext(), topicSetBy)), 0, topicSetBy.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                String topicSetOnStr = DateUtils.formatDateTime(topicInfoTextView.getContext(),
                        topicSetOn.getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);

                topicInfoTextView.setText(SpannableStringHelper.getText(
                        topicInfoTextView.getContext(), R.string.channel_topic_info,
                        topicSetByColored, topicSetOnStr));
                topicInfoTextView.setVisibility(View.VISIBLE);
            } else {
                topicInfoTextView.setText(null);
                topicInfoTextView.setVisibility(View.GONE);
            }
        }

    }

    private static class SearchHolder extends RecyclerView.ViewHolder {

        private final ChannelInfoAdapter adapter;
        private final EditText input;
        private final ImageButton clear;

        SearchHolder(View view, ChannelInfoAdapter adapter) {
            super(view);
            this.adapter = adapter;
            input = view.findViewById(R.id.member_search_input);
            clear = view.findViewById(R.id.member_search_clear);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count,
                                                        int after) { }

                @Override public void onTextChanged(CharSequence text, int start, int before,
                                                    int count) {
                    adapter.setSearchQuery(text.toString());
                    clear.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
                }

                @Override public void afterTextChanged(Editable text) { }
            });
            clear.setOnClickListener(v -> input.setText(""));
        }

        void bind(String query) {
            adapter.mSearchInput = input;
            if (!TextUtils.equals(input.getText(), query))
                input.setText(query);
            clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    public static class MemberHolder extends RecyclerView.ViewHolder {

        private ServerConnectionInfo mConnection;
        private String mChannel;
        private TextView mText;
        private ImageView mAvatar;

        public MemberHolder(View v) {
            super(v);
            mText = v.findViewById(R.id.chat_member);
            mAvatar = v.findViewById(R.id.chat_member_avatar);
            v.setOnClickListener((View view) -> {
                UserBottomSheetDialog dialog = new UserBottomSheetDialog(view.getContext());
                dialog.setConnection(mConnection);
                dialog.setSourceChannel(mChannel);
                dialog.requestData((String) mText.getTag(), mConnection.getApiInstance());
                Dialog d = dialog.show();
                if (view.getContext() instanceof MainActivity)
                    ((MainActivity) view.getContext()).setFragmentDialog(d);
            });
            v.setOnLongClickListener(view -> {
                Object nick = mText.getTag();
                if (nick instanceof String)
                    NicknameContextMenu.show(view.getContext(), mConnection, (String) nick, mChannel);
                return true;
            });
        }

        public void bind(ServerConnectionInfo connection, String channel, NickWithPrefix nickWithPrefix) {
            mConnection = connection;
            mChannel = channel;
            bindText(mText, nickWithPrefix);
            mText.setAlpha(isAway(connection, nickWithPrefix.getNick()) ? 0.55f : 1f);
            mText.setTag(nickWithPrefix.getNick());
            String account = SimosnapAvatarManager.getAccount(connection,
                    nickWithPrefix.getNick());
            SimosnapAvatarLoader.load(mAvatar, account, false, null);
        }

        private static boolean isAway(ServerConnectionInfo connection, String nick) {
            try {
                UserInfo user = connection.getApiInstance().getUserInfoApi()
                        .getUser(nick, null, null, null, null).get();
                return user != null && user.isAway();
            } catch (Exception ignored) {
                return false;
            }
        }

        public static void bindText(TextView text, NickWithPrefix nickWithPrefix) {
            char prefix = ' ';
            if (nickWithPrefix.getNickPrefixes() != null &&
                    nickWithPrefix.getNickPrefixes().length() > 0)
                prefix = nickWithPrefix.getNickPrefixes().get(0);
            int colorId = IRCColorUtils.COLOR_MEMBER_NORMAL;
            if (prefix == '~')
                colorId = IRCColorUtils.COLOR_MEMBER_OWNER;
            else if (prefix == '&')
                colorId = IRCColorUtils.COLOR_MEMBER_ADMIN;
            else if (prefix == '@')
                colorId = IRCColorUtils.COLOR_MEMBER_OP;
            else if (prefix == '%')
                colorId = IRCColorUtils.COLOR_MEMBER_HALF_OP;
            else if (prefix == '+')
                colorId = IRCColorUtils.COLOR_MEMBER_VOICE;
            text.setTextColor(IRCColorUtils.getColorById(text.getContext(), colorId));
            if (prefix != ' ')
                text.setText(prefix + nickWithPrefix.getNick());
            else
                text.setText(nickWithPrefix.getNick());
        }

    }

}

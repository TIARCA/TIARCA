package io.mrarm.irc.dialog;

import android.content.Context;

import androidx.fragment.app.Fragment;

import io.mrarm.chatlib.irc.ServerConnectionApi;
import io.mrarm.irc.MainActivity;
import io.mrarm.irc.MonitoredUserDialog;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;
import io.mrarm.irc.chat.ChatFragment;
import io.mrarm.irc.irc.MonitoredUsersManager;

/** Shared, short first-level menu for a known IRC nickname. */
public final class NicknameContextMenu {

    private NicknameContextMenu() { }

    public static void show(Context context, ServerConnectionInfo connection, String nick,
                            String sourceChannel) {
        if (context == null || connection == null || nick == null || nick.trim().isEmpty())
            return;
        String targetNick = nick.trim();
        ServerConnectionApi api = connection.getApiInstance() instanceof ServerConnectionApi
                ? (ServerConnectionApi) connection.getApiInstance() : null;
        if (api == null)
            return;
        boolean self = api.getServerConnectionData().getSupportList().getCaseMapping()
                .equals(api.getServerConnectionData().getUserNick(), targetNick);
        boolean service = connection.isKnownServiceNick(targetNick);
        boolean channelContext = isChannel(connection, sourceChannel);
        MenuBottomSheetDialog menu = new MenuBottomSheetDialog(context);
        menu.addHeader(targetNick);
        if (!self) {
            menu.addItem(R.string.action_open_private, R.drawable.ic_message, item -> {
                openPrivateConversation(context, connection, targetNick);
                return true;
            });
        }
        if (!self && canMention(context, connection, sourceChannel)) {
            menu.addItem(R.string.action_mention_user, R.drawable.ic_reply, item -> {
                mention(context, connection, sourceChannel, targetNick);
                return true;
            });
        }
        menu.addItem(R.string.action_whois, R.drawable.ic_info, item -> {
            showWhois(context, connection, targetNick, sourceChannel);
            return true;
        });
        if (!self && !service && connection.getMonitoredUsersManager().isSupported(api.getServerConnectionData())) {
            MonitoredUsersManager monitored = connection.getMonitoredUsersManager();
            boolean alreadyMonitored = monitored.isMonitored(api.getServerConnectionData(), targetNick);
            menu.addItem(alreadyMonitored ? R.string.action_edit_monitor_user : R.string.action_monitor_user,
                    R.drawable.ic_notifications, item -> {
                        MonitoredUserDialog.show(context, connection,
                                alreadyMonitored ? monitored.getMonitoredUser(api.getServerConnectionData(), targetNick) : null,
                                () -> { });
                        return true;
                    });
        }
        if (!self && !service) {
            menu.addItem(R.string.action_ignore, R.drawable.ic_close, item -> {
                IgnoreUserDialog.show(context, connection, targetNick, null, null);
                return true;
            });
        }
        if (!self && channelContext && UserBottomSheetDialog.hasOperatorPrivileges(connection, sourceChannel)) {
            menu.addItem(R.string.operator_actions, R.drawable.ic_operator_actions, item -> {
                UserBottomSheetDialog.showOperatorActions(context, connection, targetNick, sourceChannel);
                return true;
            });
        }
        menu.show();
        if (context instanceof MainActivity)
            ((MainActivity) context).setFragmentDialog(menu);
    }

    private static void showWhois(Context context, ServerConnectionInfo connection, String nick,
                                  String sourceChannel) {
        UserBottomSheetDialog dialog = new UserBottomSheetDialog(context);
        dialog.setConnection(connection);
        dialog.setSourceChannel(sourceChannel);
        dialog.requestData(nick, connection.getApiInstance());
        android.app.Dialog shown = dialog.show();
        if (context instanceof MainActivity)
            ((MainActivity) context).setFragmentDialog(shown);
    }

    private static void openPrivateConversation(Context context, ServerConnectionInfo connection, String nick) {
        if (context instanceof MainActivity)
            ((MainActivity) context).openDirectConversationForSharing(connection, nick);
        else
            connection.addStoredConversation(nick);
    }

    private static boolean canMention(Context context, ServerConnectionInfo connection, String sourceChannel) {
        if (!(context instanceof MainActivity) || !isChannel(connection, sourceChannel))
            return false;
        Fragment fragment = ((MainActivity) context).getCurrentFragment();
        return fragment instanceof ChatFragment && ((ChatFragment) fragment).getConnectionInfo() == connection &&
                sourceChannel.equals(((ChatFragment) fragment).getCurrentChannel());
    }

    private static void mention(Context context, ServerConnectionInfo connection, String sourceChannel, String nick) {
        if (!(context instanceof MainActivity))
            return;
        Fragment fragment = ((MainActivity) context).getCurrentFragment();
        if (fragment instanceof ChatFragment && ((ChatFragment) fragment).getConnectionInfo() == connection &&
                sourceChannel.equals(((ChatFragment) fragment).getCurrentChannel()))
            ((ChatFragment) fragment).getSendMessageHelper().insertMention(nick);
    }

    private static boolean isChannel(ServerConnectionInfo connection, String name) {
        if (name == null || name.isEmpty() || !(connection.getApiInstance() instanceof ServerConnectionApi))
            return false;
        return ((ServerConnectionApi) connection.getApiInstance()).getServerConnectionData()
                .getSupportList().getSupportedChannelTypes().contains(name.charAt(0));
    }
}

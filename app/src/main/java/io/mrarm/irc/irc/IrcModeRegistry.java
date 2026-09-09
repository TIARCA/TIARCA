package io.mrarm.irc.irc;

import android.content.Context;

import java.util.List;
import java.util.Locale;

import io.mrarm.chatlib.dto.HostInfoMessageInfo;
import io.mrarm.chatlib.dto.StatusMessageInfo;
import io.mrarm.chatlib.irc.IRCConnection;
import io.mrarm.irc.R;
import io.mrarm.irc.ServerConnectionInfo;

/** Shared server/network-aware descriptions and editability rules for channel and user modes. */
public final class IrcModeRegistry {

    public enum Profile { GENERIC, SIMOSNAP, IRCNET, UNDERNET, LIBERA, INSPIRCD_3, INSPIRCD_4, UNREALIRCD_6, SOLANUM, ERGO }
    public enum Target { CHANNEL, USER }

    private final Context context;
    private final Profile profile;

    private IrcModeRegistry(Context context, Profile profile) {
        this.context = context;
        this.profile = profile;
    }

    public static IrcModeRegistry forConnection(Context context, ServerConnectionInfo info, IRCConnection irc) {
        return new IrcModeRegistry(context, detectProfile(info, irc));
    }

    public Profile getProfile() { return profile; }

    public String getProfileLabel() {
        switch (profile) {
            case SIMOSNAP: return "SimosNap (InspIRCd 3)";
            case IRCNET: return "IRCnet";
            case UNDERNET: return "Undernet";
            case LIBERA: return "Libera.Chat (Solanum)";
            case INSPIRCD_3: return "InspIRCd 3";
            case INSPIRCD_4: return "InspIRCd 4";
            case UNREALIRCD_6: return "UnrealIRCd 6";
            case SOLANUM: return "Solanum";
            case ERGO: return "Ergo";
            default: return context.getString(R.string.irc_mode_profile_generic);
        }
    }

    public String description(Target target, char mode) {
        String common = target == Target.CHANNEL ? commonChannel(mode) : commonUser(mode);
        if (common != null) return common;
        return target == Target.CHANNEL ? profileChannel(mode) : profileUser(mode);
    }

    public boolean isEditable(Target target, char mode) {
        if (target == Target.CHANNEL) {
            if ((isInsp() || profile == Profile.UNREALIRCD_6) && mode == 'r') return false;
            if (profile == Profile.UNREALIRCD_6 && (mode == 'd' || mode == 'Z')) return false;
        } else {
            if (profile == Profile.UNREALIRCD_6 && (mode == 'z' || mode == 'H' || mode == 'W')) return false;
            if (isInsp() && (mode == 'r' || mode == 'o' || mode == 'k')) return false;
            if (isInsp() && (mode == 'h' || mode == 'O')) return false;
            if (profile == Profile.IRCNET && mode == 'o') return false;
            if (profile == Profile.LIBERA && (mode == 'o' || mode == 'S')) return false;
        }
        return true;
    }

    private boolean isInsp() {
        return profile == Profile.SIMOSNAP || profile == Profile.INSPIRCD_3 || profile == Profile.INSPIRCD_4;
    }

    private String commonChannel(char m) {
        switch (m) {
            case 'i': return context.getString(R.string.irc_mode_channel_i);
            case 'm': return context.getString(R.string.irc_mode_channel_m);
            case 'n': return context.getString(R.string.irc_mode_channel_n);
            case 't': return context.getString(R.string.irc_mode_channel_t);
            case 's': return context.getString(R.string.irc_mode_channel_s);
            case 'p': return context.getString(R.string.irc_mode_channel_p);
            case 'k': return context.getString(R.string.irc_mode_channel_k);
            case 'l': return context.getString(R.string.irc_mode_channel_l);
            case 'b': return context.getString(R.string.irc_mode_channel_b);
            default: return null;
        }
    }

    private String commonUser(char m) {
        switch (m) {
            case 'i': return context.getString(R.string.irc_mode_user_i);
            case 'w': return context.getString(R.string.irc_mode_user_w);
            default: return null;
        }
    }

    private String profileChannel(char m) {
        if (profile == Profile.SIMOSNAP) {
            switch (m) {
                case 'B': return context.getString(R.string.irc_mode_channel_simosnap_B);
                case 'H': return context.getString(R.string.irc_mode_channel_simosnap_H);
                case 'J': return context.getString(R.string.irc_mode_channel_simosnap_J);
                case 'X': return context.getString(R.string.irc_mode_channel_simosnap_X);
            }
        }
        if (isInsp()) {
            switch (m) {
                case 'A': return context.getString(R.string.irc_mode_channel_insp_A);
                case 'C': return context.getString(R.string.irc_mode_channel_insp_C);
                case 'D': return context.getString(R.string.irc_mode_channel_insp_D);
                case 'K': return context.getString(R.string.irc_mode_channel_insp_K);
                case 'M': return context.getString(R.string.irc_mode_channel_insp_M);
                case 'N': return context.getString(R.string.irc_mode_channel_insp_N);
                case 'O': return context.getString(R.string.irc_mode_channel_insp_O);
                case 'Q': return context.getString(R.string.irc_mode_channel_insp_Q);
                case 'R': return context.getString(R.string.irc_mode_channel_insp_R);
                case 'S': return context.getString(R.string.irc_mode_channel_insp_S);
                case 'T': return context.getString(R.string.irc_mode_channel_insp_T);
                case 'U': return context.getString(R.string.irc_mode_channel_insp_U);
                case 'c': return context.getString(R.string.irc_mode_channel_insp_c);
                case 'r': return context.getString(R.string.irc_mode_channel_registered);
                case 'u': return context.getString(R.string.irc_mode_channel_insp_u);
                case 'z': return context.getString(R.string.irc_mode_channel_tls_only);
            }
        }
        if (profile == Profile.LIBERA || profile == Profile.SOLANUM) {
            switch (m) {
                case 'c': return context.getString(R.string.irc_mode_channel_libera_c);
                case 'C': return context.getString(R.string.irc_mode_channel_libera_C);
                case 'F': return context.getString(R.string.irc_mode_channel_libera_F);
                case 'g': return context.getString(R.string.irc_mode_channel_libera_g);
                case 'Q': return context.getString(R.string.irc_mode_channel_libera_Q);
                case 'r': return context.getString(R.string.irc_mode_channel_libera_r);
                case 'R': return context.getString(R.string.irc_mode_channel_libera_R);
                case 'S': return context.getString(R.string.irc_mode_channel_libera_S);
                case 'T': return context.getString(R.string.irc_mode_channel_libera_T);
                case 'u': return context.getString(R.string.irc_mode_channel_libera_u);
                case 'z': return context.getString(R.string.irc_mode_channel_libera_z);
            }
        }
        if (profile == Profile.UNREALIRCD_6) {
            switch (m) {
                case 'c': return context.getString(R.string.irc_mode_channel_no_color);
                case 'C': return context.getString(R.string.irc_mode_channel_no_ctcp);
                case 'D': return context.getString(R.string.irc_mode_channel_delay_join);
                case 'G': return context.getString(R.string.irc_mode_channel_censor);
                case 'H': return context.getString(R.string.irc_mode_channel_history);
                case 'K': return context.getString(R.string.irc_mode_channel_no_knock);
                case 'M': return context.getString(R.string.irc_mode_channel_registered_speak);
                case 'N': return context.getString(R.string.irc_mode_channel_no_nickchange);
                case 'O': return context.getString(R.string.irc_mode_channel_oper_only);
                case 'P': return context.getString(R.string.irc_mode_channel_permanent);
                case 'Q': return context.getString(R.string.irc_mode_channel_no_kick);
                case 'R': return context.getString(R.string.irc_mode_channel_registered_join);
                case 'r': return context.getString(R.string.irc_mode_channel_registered);
                case 'S': return context.getString(R.string.irc_mode_channel_strip_color);
                case 'T': return context.getString(R.string.irc_mode_channel_no_notice);
                case 'V': return context.getString(R.string.irc_mode_channel_no_invite);
                case 'z': return context.getString(R.string.irc_mode_channel_tls_only);
                case 'Z': return context.getString(R.string.irc_mode_channel_all_tls);
            }
        }
        if (profile == Profile.IRCNET) {
            switch (m) {
                case 'a': return context.getString(R.string.irc_mode_channel_ircnet_a);
                case 'q': return context.getString(R.string.irc_mode_channel_ircnet_q);
            }
        }
        return context.getString(R.string.irc_mode_unknown);
    }

    private String profileUser(char m) {
        if (isInsp()) {
            switch (m) {
                case 'B': return context.getString(R.string.irc_mode_user_bot);
                case 'c': return context.getString(R.string.irc_mode_user_common_channels);
                case 'd': return context.getString(R.string.irc_mode_user_channel_deaf);
                case 'D': return context.getString(R.string.irc_mode_user_private_deaf);
                case 'g': return context.getString(R.string.irc_mode_user_callerid);
                case 'h': return context.getString(R.string.irc_mode_user_helpop);
                case 'I': return context.getString(R.string.irc_mode_user_hide_channels);
                case 'k': return context.getString(R.string.irc_mode_user_services_protected);
                case 'L': return context.getString(R.string.irc_mode_user_antiredirect);
                case 'N': return context.getString(R.string.irc_mode_user_nohistory);
                case 'O': return context.getString(R.string.irc_mode_user_override);
                case 'o': return context.getString(R.string.irc_mode_user_oper);
                case 'r': return context.getString(R.string.irc_mode_user_registered);
                case 'R': return context.getString(R.string.irc_mode_user_registered_only_pm);
                case 'S': return context.getString(R.string.irc_mode_user_strip_formatting);
                case 'T': return context.getString(R.string.irc_mode_user_noctcp);
                case 'x': return context.getString(R.string.irc_mode_user_cloak);
                case 'z': return context.getString(R.string.irc_mode_user_secure_messages);
                case 'V': if (profile == Profile.SIMOSNAP) return context.getString(R.string.irc_mode_user_noinvites);
            }
            return context.getString(R.string.irc_mode_user_insp_unknown);
        }
        if (profile == Profile.UNDERNET) {
            switch (m) {
                case 'd': return context.getString(R.string.irc_mode_user_undernet_d);
                case 'x': return context.getString(R.string.irc_mode_user_undernet_x);
                case 's': return context.getString(R.string.irc_mode_user_server_notices);
                case 'o': return context.getString(R.string.irc_mode_user_oper);
            }
            return context.getString(R.string.irc_mode_user_network_specific);
        }
        if (profile == Profile.LIBERA || profile == Profile.SOLANUM) {
            switch (m) {
                case 'D': return context.getString(R.string.irc_mode_user_libera_D);
                case 'g': return context.getString(R.string.irc_mode_user_libera_g);
                case 'Q': return context.getString(R.string.irc_mode_user_libera_Q);
                case 'R': return context.getString(R.string.irc_mode_user_libera_R);
                case 'u': return context.getString(R.string.irc_mode_user_libera_u);
                case 'x': return context.getString(R.string.irc_mode_user_cloak);
                case 's': return context.getString(R.string.irc_mode_user_server_notices);
                case 'o': return context.getString(R.string.irc_mode_user_oper);
            }
            return context.getString(R.string.irc_mode_user_network_specific);
        }
        if (profile == Profile.UNREALIRCD_6) {
            switch (m) {
                case 'B': return context.getString(R.string.irc_mode_user_bot);
                case 'd': return context.getString(R.string.irc_mode_user_channel_deaf);
                case 'D': return context.getString(R.string.irc_mode_user_private_deaf);
                case 'G': return context.getString(R.string.irc_mode_user_censor);
                case 'H': return context.getString(R.string.irc_mode_user_hide_oper);
                case 'I': return context.getString(R.string.irc_mode_user_hide_idle);
                case 'W': return context.getString(R.string.irc_mode_user_show_whois);
                case 'x': return context.getString(R.string.irc_mode_user_cloak);
                case 'Z': return context.getString(R.string.irc_mode_user_secure_messages);
                case 'z': return context.getString(R.string.irc_mode_user_tls);
            }
        }
        if (profile == Profile.IRCNET) {
            switch (m) {
                case 's': return context.getString(R.string.irc_mode_user_server_notices);
                case 'o': return context.getString(R.string.irc_mode_user_oper);
            }
        }
        if (profile == Profile.ERGO) {
            switch (m) {
                case 'B': return context.getString(R.string.irc_mode_user_bot);
                case 'R': return context.getString(R.string.irc_mode_user_registered_only_pm);
                case 's': return context.getString(R.string.irc_mode_user_server_notices);
            }
        }
        return context.getString(R.string.irc_mode_unknown);
    }

    private static Profile detectProfile(ServerConnectionInfo info, IRCConnection irc) {
        String version = "";
        String server = "";
        List<StatusMessageInfo> messages = irc.getServerConnectionData().getServerStatusData().getMessages();
        synchronized (messages) {
            for (StatusMessageInfo message : messages) {
                if (message instanceof HostInfoMessageInfo) {
                    HostInfoMessageInfo host = (HostInfoMessageInfo) message;
                    if (host.getVersion() != null) version = host.getVersion().toLowerCase(Locale.ROOT);
                    if (host.getServerName() != null) server = host.getServerName().toLowerCase(Locale.ROOT);
                }
            }
        }
        String address = info.getServerAddress() == null ? "" : info.getServerAddress().toLowerCase(Locale.ROOT);
        String probe = version + " " + server + " " + address;
        if (probe.contains("simosnap")) return Profile.SIMOSNAP;
        if (probe.contains("libera.chat")) return Profile.LIBERA;
        if (probe.contains("undernet.org") || probe.contains("undernet")) return Profile.UNDERNET;
        if (probe.contains("ircnet") || probe.contains("irc.atw-inter.net")) return Profile.IRCNET;
        if (probe.contains("inspircd-4") || probe.contains("inspircd 4")) return Profile.INSPIRCD_4;
        if (probe.contains("inspircd-3") || probe.contains("inspircd 3")) return Profile.INSPIRCD_3;
        if (probe.contains("unrealircd-6") || probe.contains("unrealircd 6") || probe.contains("unreal6")) return Profile.UNREALIRCD_6;
        if (probe.contains("solanum")) return Profile.SOLANUM;
        if (probe.contains("ergo")) return Profile.ERGO;
        return Profile.GENERIC;
    }

    public static String advertisedUserModes(IRCConnection irc) {
        String modes = null;
        List<StatusMessageInfo> messages = irc.getServerConnectionData().getServerStatusData().getMessages();
        synchronized (messages) {
            for (StatusMessageInfo message : messages) {
                if (message instanceof HostInfoMessageInfo) {
                    String value = ((HostInfoMessageInfo) message).getUserModes();
                    if (value != null && !value.isEmpty()) modes = value;
                }
            }
        }
        return modes;
    }

    private IrcModeRegistry() { context = null; profile = Profile.GENERIC; }
}

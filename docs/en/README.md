# Complete guide to TIARCA 0.9.3

[← Languages](../README.md)

TIARCA is an Android IRC client designed for everyday chatting and for users who need advanced IRC features in a modern interface.

## 1. Installation and first connection
Download the signed APK from GitHub Releases, install it, then create a server/network configuration. TIARCA supports multiple independent server configurations.

![server configuration](../images/12_configurazione_server.jpg)

## 2. Servers, TLS and SASL
Configure network/server name, address, port, nickname and connection parameters. TLS/SSL and SASL authentication are supported. Default channels can be edited and networks can be manually reordered. Never expose passwords or SASL credentials in screenshots or bug reports.

## 3. Main interface
The drawer contains servers, channels and private conversations. Conversation tabs provide fast navigation. Unread activity and mentions are tracked separately. **Monitored users** is available near the top of the drawer and **Settings** stays at the bottom of the overflow menu.

![drawer](../images/01_drawer_menu.jpg)

## 4. Channels
Channels support normal IRC messaging, nick/channel/command autocomplete, mIRC formatting/colors and stored chat history. Permissions and moderation remain controlled by the IRC server.

![channel chat](../images/02_chat_canale.jpg)

## 5. Private conversations
Private conversations provide direct **Send** and **Ignore** actions in the toolbar. When caller-ID `+g` is active and you deliberately start a private conversation, TIARCA automatically performs the appropriate `ACCEPT +nick`; it does not add the nick when `+g` is inactive.

![private conversation](../images/03_chat_privata_pvt.jpg)

## 6. WHOIS and WHOWAS
WHOIS displays information returned by the server and exposes relevant user actions. TIARCA also presents WHOWAS replies as structured information instead of leaving them only as raw server text. Available fields depend on the network.

![WHOIS](../images/04_whois.jpg)

## 7. User modes
The user-mode editor shows current modes and descriptions. TIARCA uses server-aware mode knowledge where possible and avoids treating every IRC daemon identically. Server/services-managed modes are protected from inappropriate local editing.

![user modes](../images/06_modalita_utente.jpg)

## 8. Channel modes
The channel-mode editor displays active state and descriptions. Mode parameters and semantics can differ between IRC implementations; the server remains authoritative and your privileges determine what can be changed.

![channel modes](../images/07_modalita_canale.jpg)

## 9. Bans and exceptions
TIARCA provides a unified interface for channel ban and exception lists. Masks can be inspected and, with sufficient privileges, changed through the UI.

![bans/exceptions](../images/08_ban_eccezioni.jpg)

## 10. MONITOR
On compatible networks TIARCA supports IRC `MONITOR`. The dedicated Monitored Users screen manages the list and displays known online/offline state.

![monitored users](../images/05_utenti_monitorati.jpg)

## 11. Caller-ID +g and ACCEPT
On supporting networks, `+g` restricts who may privately message you. TIARCA handles numeric 718 and provides ACCEPT actions. In 0.9.2, user-initiated private chats automatically add the target to ACCEPT only while your current user mode includes `+g`.

## 12. Search, history and mentions
Search locates messages in stored history and navigates to the result in chronological context. Mentions use a separate counter from ordinary unread messages.

![message search](../images/09_ricerca_messaggi.jpg)

## 13. Ignore
Ignore management filters unwanted users. A direct Ignore action is available in private-chat toolbars and is also integrated into user-information workflows.

## 14. Files and media
TIARCA can share images, files, audio and video through temporary links. Private conversations expose the Send action directly in the toolbar. Remember that the resulting link is sent through IRC to the conversation recipients.

## 15. IRC commands and Quick Commands
TIARCA directly supports commands including `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` and `/who`.

Configurable **Quick Commands** provide trigger-based shortcuts such as `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` and `!dizionario`.

![quick commands](../images/14_comandi_rapidi.jpg)

## 16. Appearance and message format
Light and dark themes are available together with extensive app-color customization, chat font options and configurable message formatting, including the optional right-side clock.

![interface settings](../images/10_impostazioni_interfaccia.jpg)
![colors](../images/13_personalizzazione_colori.jpg)
![message format](../images/11_formato_messaggi.jpg)

## 17. Updates
The integrated update checker is opt-in and can periodically check official GitHub releases. Installation is assisted; official APKs remain distributed through this repository's Releases page.

## 18. Backup and device transfer
TIARCA supports Android encrypted backup/device-transfer behavior separately from TIARCA's manual backup. Exact Android backup availability can depend on OS/device configuration.

## 19. IRC compatibility
IRC networks run different daemons and extensions. TIARCA contains compatibility knowledge for common server families and prefers structured IRC numerics/parameters, but server-side features only work when the connected network implements them.

## 20. Troubleshooting
**Cannot connect:** verify host, port, TLS, nickname and SASL credentials.  
**Cannot change a mode:** you may lack privileges or the mode may be controlled by server/services.  
**MONITOR unavailable:** the network may not implement it.  
**Command errors:** command availability and syntax can vary by daemon.  
**No update notification:** check that update checking is enabled and GitHub is reachable.  
**Reporting a bug:** include TIARCA version, Android version, IRC server/daemon if known and reproduction steps; remove passwords, tokens, personal hosts/IPs and other sensitive data.

## 21. Privacy and security
IRC is not inherently end-to-end encrypted. TLS protects the client-to-server transport when enabled, but does not make IRC E2EE. Do not post passwords, SASL tokens or personal information in channels, screenshots or GitHub issues.

## 22. License and origin
TIARCA is based on Revolution IRC by MrARM/MCMrARM and continues as an independent project under GNU GPLv3, preserving applicable original and dependency attributions.

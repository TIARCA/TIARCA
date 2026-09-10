# Vollständige Anleitung für TIARCA 0.9.2

[← Sprachen](../README.md)

TIARCA ist ein moderner IRC-Client für Android und eine unabhängig weiterentwickelte Abspaltung von Revolution IRC.

## Installation und Server
Installiere die signierte APK aus GitHub Releases. Mehrere IRC-Netzwerke können getrennt konfiguriert und manuell sortiert werden. Serveradresse, Port, Nickname, TLS/SSL, SASL und automatisch zu betretende Kanäle sind konfigurierbar. Zugangsdaten dürfen niemals in Screenshots oder Fehlerberichten veröffentlicht werden.

![Serverkonfiguration](../images/12_configurazione_server.jpg)

## Oberfläche, Kanäle und private Chats
Der Drawer enthält Server, Kanäle und private Unterhaltungen; Erwähnungen und normale ungelesene Nachrichten werden getrennt gezählt. **Überwachte Benutzer** sind oben erreichbar, **Einstellungen** bleiben unten im `…`-Menü. Kanäle unterstützen Verlauf, Autovervollständigung und mIRC-Formatierung. Private Chats bieten direkte Aktionen **Senden** und **Ignorieren**.

![Drawer](../images/01_drawer_menu.jpg)
![Kanal](../images/02_chat_canale.jpg)
![Privatgespräch](../images/03_chat_privata_pvt.jpg)

## WHOIS und WHOWAS
WHOIS zeigt vom Server gelieferte Benutzerinformationen und passende Aktionen. WHOWAS wird strukturiert dargestellt, statt nur als roher Servertext zu erscheinen. Welche Felder vorhanden sind, hängt vom IRC-Netzwerk ab.

![WHOIS](../images/04_whois.jpg)

## Benutzer- und Kanalmodi
TIARCA zeigt aktive Modi mit Beschreibungen und berücksichtigt Unterschiede zwischen IRC-Serverfamilien. Server-/Services-gesteuerte Modi werden geschützt. Kanalmodi können nur mit den vom Server gewährten Rechten geändert werden.

![Benutzermodi](../images/06_modalita_utente.jpg)
![Kanalmodi](../images/07_modalita_canale.jpg)

## Bans und Ausnahmen
Ban- und Ausnahmelisten werden in einer einheitlichen Oberfläche angezeigt und können mit ausreichenden Rechten bearbeitet werden.

![Bans/Ausnahmen](../images/08_ban_eccezioni.jpg)

## MONITOR
Auf kompatiblen Netzen unterstützt TIARCA IRC `MONITOR`. Die Ansicht **Überwachte Benutzer** verwaltet die Liste und zeigt den bekannten Online-/Offline-Status.

![überwachte Benutzer](../images/05_utenti_monitorati.jpg)

## Caller-ID +g und ACCEPT
`+g` kann private Nachrichten auf zugelassene Benutzer beschränken. TIARCA verarbeitet Numeric 718 und ACCEPT. Seit 0.9.2 wird beim bewusst gestarteten Privatgespräch automatisch `ACCEPT +nick` ausgeführt, aber nur wenn `+g` tatsächlich aktiv ist.

## Suche, Verlauf, Erwähnungen und Ignore
Die Nachrichtensuche springt zum Treffer im zeitlichen Kontext. Erwähnungen besitzen einen eigenen Zähler. Unerwünschte Benutzer können über Ignore gefiltert werden; in privaten Chats ist die Aktion direkt in der Toolbar verfügbar.

![Nachrichtensuche](../images/09_ricerca_messaggi.jpg)

## Dateien und Medien
Bilder, Dateien, Audio und Video können über temporäre Links geteilt werden. Der erzeugte Link wird über IRC an die Empfänger gesendet.

## IRC-Befehle und Schnellbefehle
Direkt unterstützt werden u. a. `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` und `/who`. Konfigurierbare Schnellbefehle umfassen Trigger wie `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` und `!dizionario`.

![Schnellbefehle](../images/14_comandi_rapidi.jpg)

## Darstellung
Helles und dunkles Theme, App-Farben, Chat-Schrift und Nachrichtenformat sind anpassbar; dazu gehört eine optionale Uhrzeit auf der rechten Nachrichtenseite.

![Oberfläche](../images/10_impostazioni_interfaccia.jpg)
![Farben](../images/13_personalizzazione_colori.jpg)
![Nachrichtenformat](../images/11_formato_messaggi.jpg)

## Updates und Backup
Der integrierte Update-Check ist optional und prüft offizielle GitHub Releases. TIARCA unterstützt außerdem Androids verschlüsseltes Backup/Geräteübertragung getrennt vom manuellen TIARCA-Backup.

## Kompatibilität und Fehlerbehebung
IRC-Netze verwenden unterschiedliche Server und Erweiterungen; serverseitige Funktionen sind nur verfügbar, wenn das Netzwerk sie unterstützt. Bei Verbindungsproblemen Host, Port, TLS, Nickname und SASL prüfen. Bei Mode-Fehlern Rechte und Serverregeln prüfen. Bei Bugreports TIARCA-/Android-Version und reproduzierbare Schritte angeben, aber Passwörter, Tokens, persönliche Hosts/IPs entfernen.

## Datenschutz, Herkunft und Lizenz
IRC ist nicht automatisch Ende-zu-Ende-verschlüsselt. TLS schützt die Verbindung zum Server, nicht die gesamte Kommunikation E2EE. TIARCA basiert auf Revolution IRC von MrARM/MCMrARM und wird unabhängig unter GNU GPLv3 weiterentwickelt.

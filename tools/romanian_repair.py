#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-ro/strings.xml"

STRING_FIXES = {
    "tab_server": "Server",
    "message_whois": "Informații despre utilizatorul %1$s",
    "message_mode_set": "a setat %1$s",
    "message_mode_unset": "a eliminat %1$s",
    "message_mode_gave": "a acordat %1$s",
    "message_mode_removed": "a retras %1$s",
    "message_mode_gave_to": "%1$s lui %2$s",
    "message_mode_removed_from": "%1$s de la %2$s",
    "message_mode_channel_value": "modul canalului \\'%1$s\\' la %2$s",
    "message_mode_channel_ban": "un ban pentru %1$s",
    "message_mode_nick_owner": "statut de proprietar al canalului",
    "message_mode_nick_admin": "statut de administrator al canalului",
    "message_mode_nick_op": "statut de operator al canalului",
    "message_mode_nick_half_op": "statut half-op al canalului",
    "message_mode_nick_voice": "voice",
    "message_ctcp_version": "Solicitare CTCP VERSION primită",
    "command_send_raw": "Trimite ca comandă raw",
    "channel_members": "Membri",
    "mention_position": "Mențiunea %1$d din %2$d",
    "unread_go_latest": "Mergi la ultimul mesaj și marchează ca citit",
    "action_open": "Deschide",
    "action_back": "Înapoi",
    "network_catalog_search_hint": "Caută rețele",
    "network_catalog_sort_users": "Cei mai mulți utilizatori",
    "network_catalog_approximately": "aproximativ %1$s utilizatori · %2$s canale",
    "network_catalog_unavailable": "numărul de utilizatori nu este disponibil",
    "action_reorder": "Reordonează",
    "action_copy": "Copiază",
    "action_clone": "Clonează",
    "action_finish": "Finalizează",
    "action_search": "Caută",
    "action_message_user": "Trimite mesaj utilizatorului",
    "action_exit": "Ieși",
    "action_reply": "Răspunde",
    "action_accept": "Acceptă",
    "action_reject": "Respinge",
    "action_export": "Exportă",
    "action_import_theme": "Importă tema",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban pe host",
    "operator_tban": "TBAN pe host",
    "operator_mute": "Mute pe host",
    "operator_audi": "Audi pe ident",
    "operator_unban_host": "Elimină banul exact al hostului",
    "operator_voice": "Acordă voice",
    "operator_mask": "Mască de ban",
    "operator_historical_data_desc": "Hostul și identul aparțin celei mai recente conexiuni a utilizatorului.",
    "operator_choose_channel": "Alege canalul în care ești operator",
    "operator_not_authorised": "Acțiunile de operator necesită statut half-op sau mai mare pe acest canal.",
    "operator_reason_optional": "Motiv (opțional)",
    "operator_custom_reason": "Mesaj personalizat…",
    "search_messages_ready": "Caută în ultimele %1$d mesaje salvate.",
    "search_messages_count": "Potrivirea %1$d din %2$d",
    "service_title": "Conectat la IRC",
}


def replace_string(text, name, value):
    pat = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.S)
    out, n = pat.subn(lambda m: m.group(1) + value + m.group(3), text, count=1)
    if n != 1:
        raise RuntimeError(f"Expected one string {name}, found {n}")
    return out


def main():
    text = PATH.read_text(encoding="utf-8")
    for name, value in STRING_FIXES.items():
        text = replace_string(text, name, value)
    PATH.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()

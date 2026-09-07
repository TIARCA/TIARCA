#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-fi/strings.xml"

STRING_FIXES = {
    "tab_server": "Palvelin",
    "message_whois": "%1$s:n käyttäjätiedot",
    "message_mode_set": "asetti %1$s",
    "message_mode_unset": "poisti %1$s",
    "message_mode_gave": "antoi %1$s",
    "message_mode_removed": "poisti %1$s",
    "message_mode_gave_to": "%1$s käyttäjälle %2$s",
    "message_mode_removed_from": "%1$s käyttäjältä %2$s",
    "message_mode_channel_value": "kanavatilan \\'%1$s\\' arvoon %2$s",
    "message_mode_channel_ban": "ban käyttäjälle %1$s",
    "message_mode_nick_owner": "kanavan omistajan tila",
    "message_mode_nick_admin": "kanavan ylläpitäjän tila",
    "message_mode_nick_op": "kanavaoperaattorin tila",
    "message_mode_nick_half_op": "kanavan half-op-tila",
    "message_mode_nick_voice": "voice",
    "message_ctcp_version": "CTCP VERSION -pyyntö vastaanotettu",
    "command_send_raw": "Lähetä raw-komentona",
    "channel_members": "Jäsenet",
    "action_mentions": "Maininnat",
    "mention_position": "Maininta %1$d / %2$d",
    "action_edit": "Muokkaa",
    "action_connect": "Yhdistä",
    "action_open": "Avaa",
    "action_back": "Takaisin",
    "network_catalog_search_hint": "Hae verkkoja",
    "network_catalog_sort_users": "Eniten käyttäjiä",
    "network_catalog_approximately": "noin %1$s käyttäjää · %2$s kanavaa",
    "network_catalog_unavailable": "käyttäjämäärä ei saatavilla",
    "action_reorder": "Järjestä uudelleen",
    "action_copy": "Kopioi",
    "action_clone": "Kloonaa",
    "action_finish": "Valmis",
    "action_search": "Hae",
    "action_message_user": "Lähetä viesti käyttäjälle",
    "action_reset": "Palauta",
    "action_exit": "Poistu",
    "action_share": "Jaa",
    "action_reject": "Hylkää",
    "action_export": "Vie",
    "operator_actions": "Operaattorin toiminnot",
    "operator_actions_for": "Operaattorin toiminnot kanavalla %1$s",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban hostille",
    "operator_tban": "TBAN hostille",
    "operator_mute": "Mute hostille",
    "operator_audi": "Audi identille",
    "operator_unban_host": "Poista hostin tarkka ban",
    "operator_voice": "Anna voice",
    "operator_mask": "Ban-mask",
    "operator_historical_data_desc": "Host ja ident kuuluvat käyttäjän viimeisimpään yhteyteen.",
    "operator_choose_channel": "Valitse kanava, jolla olet operaattori",
    "operator_not_authorised": "Operaattoritoiminnot edellyttävät half-op-tilaa tai korkeampaa tällä kanavalla.",
    "operator_reason_optional": "Syy (valinnainen)",
    "operator_custom_reason": "Mukautettu viesti…",
    "operator_confirm_title": "Vahvista operaattoritoiminto",
    "operator_confirm_action": "Käytetäänkö toimintoa %1$s käyttäjään %2$s kanavalla %3$s?",
    "search_messages_count": "Osumia %1$d / %2$d",
}

PLURAL_FIXES = {
    "unread_message_counter": {
        "one": "1 lukematon viesti",
        "other": "%1$d lukematonta viestiä",
    },
    "mention_counter": {
        "one": "1 maininta",
        "other": "%1$d mainintaa",
    },
}


def replace_string(text, name, value):
    pat = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.S)
    out, n = pat.subn(lambda m: m.group(1) + value + m.group(3), text, count=1)
    if n != 1:
        raise RuntimeError(f"Expected one string {name}, found {n}")
    return out


def replace_plural(text, name, values):
    pat = re.compile(r'(<plurals\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</plurals>)', re.S)
    m = pat.search(text)
    if not m:
        raise RuntimeError(f"Missing plurals {name}")
    body = m.group(2)
    for quantity, value in values.items():
        ip = re.compile(r'(<item\s+quantity="' + re.escape(quantity) + r'"[^>]*>)(.*?)(</item>)', re.S)
        body, n = ip.subn(lambda x, v=value: x.group(1) + v + x.group(3), body, count=1)
        if n != 1:
            raise RuntimeError(f"Expected one {name}/{quantity}, found {n}")
    return text[:m.start()] + m.group(1) + body + m.group(3) + text[m.end():]


def main():
    text = PATH.read_text(encoding="utf-8")
    for name, value in STRING_FIXES.items():
        text = replace_string(text, name, value)
    for name, values in PLURAL_FIXES.items():
        text = replace_plural(text, name, values)
    PATH.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()

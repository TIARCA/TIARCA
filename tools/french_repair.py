#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-fr/strings.xml"

STRING_FIXES = {
    "tab_server": "Serveur",
    "message_host_info": "Le nom du serveur est %1$s, exécutant %2$s. Modes utilisateur pris en charge : %3$s, modes de canal pris en charge : %4$s",
    "message_whois": "Informations utilisateur de %1$s",
    "message_mode_set": "a défini %1$s",
    "message_mode_unset": "a retiré %1$s",
    "message_mode_gave": "a donné %1$s",
    "message_mode_removed": "a retiré %1$s",
    "message_mode_channel_value": "mode de canal \\'%1$s\\' à %2$s",
    "message_mode_channel_ban": "un ban sur %1$s",
    "message_mode_nick_owner": "statut de propriétaire du canal",
    "message_mode_nick_admin": "statut d’administrateur du canal",
    "message_mode_nick_op": "statut d’opérateur du canal",
    "message_mode_nick_half_op": "statut half-op du canal",
    "message_mode_nick_voice": "voice",
    "message_ctcp_version": "Requête CTCP VERSION reçue",
    "command_send_raw": "Envoyer comme commande raw",
    "channel_topic": "Sujet",
    "channel_members": "Membres",
    "action_mentions": "Mentions",
    "mention_position": "Mention %1$d sur %2$d",
    "unread_go_latest": "Aller au dernier message et marquer comme lu",
    "action_settings": "Paramètres",
    "action_reconnect": "Reconnecter",
    "action_open": "Ouvrir",
    "network_catalog_plaintext": "connexion non chiffrée",
    "action_reorder": "Réorganiser",
    "action_copy": "Copier",
    "action_clone": "Cloner",
    "action_finish": "Terminer",
    "action_search": "Rechercher",
    "action_message_user": "Écrire à l’utilisateur",
    "action_exit": "Quitter",
    "action_reply": "Répondre",
    "action_rename": "Renommer",
    "action_import_theme": "Importer un thème",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban de l’hôte",
    "operator_tban": "TBAN de l’hôte",
    "operator_mute": "Mute de l’hôte",
    "operator_audi": "Audi de l’ident",
    "operator_unban_host": "Supprimer le ban exact de l’hôte",
    "operator_voice": "Donner voice",
    "operator_mask": "Masque de ban",
    "operator_historical_data_desc": "L’hôte et l’ident appartiennent à la connexion la plus récente de l’utilisateur.",
    "operator_choose_channel": "Choisir le canal où vous êtes opérateur",
    "operator_not_authorised": "Les actions d’opérateur nécessitent le statut half-op ou supérieur dans ce canal.",
    "operator_reason_optional": "Motif (facultatif)",
    "operator_custom_reason": "Message personnalisé…",
    "operator_command_sent": "Commande envoyée",
    "search_messages_ready": "Rechercher dans les %1$d derniers messages enregistrés.",
    "search_messages_count": "Correspondance %1$d sur %2$d",
    "format_italic": "Italique",
    "service_title": "Connecté à IRC",
}

PLURAL_FIXES = {
    "mention_counter": {
        "one": "1 mention",
        "other": "%1$d mentions",
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

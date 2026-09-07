#!/usr/bin/env python3
from pathlib import Path
import re

# Human-reviewed TIARCA translations. English strings.xml is the source of truth;
# Italian is used as a second semantic reference for IRC-specific UI terminology.
FIXES = {
    "es": {
        "tab_server": "Servidor",
        "message_whois": "Información de usuario de %1$s",
        "message_mode_set": "estableció %1$s",
        "message_mode_unset": "quitó %1$s",
        "message_mode_nick_owner": "estado de propietario del canal",
        "message_mode_nick_admin": "estado de administrador del canal",
        "message_mode_nick_op": "estado de operador del canal",
        "message_mode_nick_half_op": "estado de half-op del canal",
        "message_mode_nick_voice": "voice",
        "action_open": "Abrir",
        "action_copy": "Copiar",
        "action_clone": "Clonar",
        "action_message_user": "Enviar mensaje al usuario",
        "action_rename": "Renombrar",
        "operator_actions": "Acciones de operador",
        "operator_actions_for": "Acciones de operador para %1$s",
        "operator_kick": "Kick",
        "operator_tban": "TBAN del host",
        "operator_mute": "Mute del host",
        "operator_audi": "Audi del ident",
        "operator_unban_host": "Quitar ban exacto del host",
        "operator_voice": "Dar voice",
        "operator_mask": "Máscara de ban",
        "operator_historical_data": "Datos históricos de WHOWAS",
        "operator_historical_data_desc": "El host y el ident pertenecen a la conexión más reciente del usuario.",
        "operator_choose_channel": "Elegir canal donde eres operador",
        "operator_not_authorised": "Las acciones de operador requieren estado de half-op o superior en este canal.",
        "pref_header_interface": "Interfaz",
        "entry_username": "Nombre de usuario",
        "message_format_time": "hora",
        "message_format_sender": "remitente",
        "message_format_message": "mensaje",
        "message_format_time_add": "Hora",
        "message_format_sender_add": "Remitente",
        "notification_rule_server": "Servidor",
        "notification_sound": "Sonido",
        "notification_vibration": "Vibración",
        "duration_types": "__SKIP__",
        "edit_command_alias_type": "Tipo",
        "edit_command_alias_syntax": "Sintaxis",
        "edit_command_alias_channel": "Canal",
        "user_server": "Servidor",
        "dcc_download_dir_application": "Aplicación",
        "ban_list_host": "Host/máscara",
        "ban_list_cleanup_none": "No se encontraron bans aptos para la limpieza.",
        "channel_mode_desc_u": "Ocultar entre sí a los usuarios sin privilegios",
    }
}


def replace_string(text: str, name: str, value: str) -> str:
    if value == "__SKIP__":
        return text
    pat = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.S)
    text2, n = pat.subn(lambda m: m.group(1) + value + m.group(3), text, count=1)
    if n != 1:
        raise RuntimeError(f"Expected exactly one <string> named {name}, found {n}")
    return text2


def main():
    root = Path(__file__).resolve().parents[1]
    for locale, fixes in FIXES.items():
        path = root / "app" / "src" / "main" / "res" / f"values-{locale}" / "strings.xml"
        text = path.read_text(encoding="utf-8")
        for name, value in fixes.items():
            text = replace_string(text, name, value)
        path.write_text(text, encoding="utf-8")
        print(f"Updated {path} ({sum(v != '__SKIP__' for v in fixes.values())} strings)")

if __name__ == "__main__":
    main()

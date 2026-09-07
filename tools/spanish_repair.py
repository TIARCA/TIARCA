#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-es/strings.xml"

STRING_FIXES = {
    "tab_server": "Servidor",
    "message_whois": "Información de usuario de %1$s",
    "message_mode_set": "ha establecido %1$s",
    "message_mode_unset": "ha quitado %1$s",
    "message_mode_gave": "ha dado %1$s",
    "message_mode_removed": "ha retirado %1$s",
    "message_mode_channel_value": "modo de canal \\'%1$s\\' a %2$s",
    "message_mode_channel_ban": "un ban sobre %1$s",
    "message_mode_nick_owner": "estado de propietario del canal",
    "message_mode_nick_admin": "estado de administrador del canal",
    "message_mode_nick_op": "estado de operador del canal",
    "message_mode_nick_half_op": "estado de half-op del canal",
    "message_mode_nick_voice": "voice",
    "message_ctcp_version": "Recibida una solicitud CTCP VERSION",
    "command_send_raw": "Enviar como comando raw",
    "mention_position": "Mención %1$d de %2$d",
    "unread_go_latest": "Ir al último mensaje y marcar como leído",
    "action_open": "Abrir",
    "network_catalog_sort_users": "Más usuarios",
    "network_catalog_approximately": "aproximadamente %1$s usuarios · %2$s canales",
    "network_catalog_unavailable": "número de usuarios no disponible",
    "action_copy": "Copiar",
    "action_clone": "Clonar",
    "action_message_user": "Escribir al usuario",
    "action_rename": "Renombrar",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban de host",
    "operator_tban": "TBAN de host",
    "operator_mute": "Mute de host",
    "operator_audi": "Audi de ident",
    "operator_unban_host": "Eliminar ban exacto de host",
    "operator_voice": "Dar voice",
    "operator_mask": "Máscara de ban",
    "operator_historical_data_desc": "El host y el ident pertenecen a la conexión más reciente del usuario.",
    "operator_choose_channel": "Elegir canal donde eres operador",
    "operator_not_authorised": "Las acciones de operador requieren estado half-op o superior en este canal.",
    "operator_reason_optional": "Motivo (opcional)",
    "operator_custom_reason": "Mensaje personalizado…",
    "search_messages_ready": "Buscar entre los últimos %1$d mensajes guardados.",
    "search_messages_count": "Coincidencia %1$d de %2$d",
    "format_italic": "Cursiva",
    "service_title": "Conectado a IRC",
    "value_preset": "Preestablecido",
    "entry_name": "Nombre",
    "entry_username": "Nombre de usuario",
    "pref_header_interface": "Interfaz",
    "message_format_time": "hora",
    "message_format_sender": "remitente",
    "message_format_message": "mensaje",
    "message_format_sender_prefix": "prefijo",
    "message_format_time_add": "Hora",
    "message_format_sender_add": "Remitente",
    "message_format_message_add": "Mensaje",
    "message_format_wrap_anchor_add": "Anclaje de ajuste",
    "message_format_sender_prefix_add": "Prefijo del remitente",
}

PLURAL_FIXES = {
    "mention_counter": {
        "one": "1 mención",
        "other": "%1$d menciones",
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

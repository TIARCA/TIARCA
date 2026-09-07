#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-pt-rBR/strings.xml"

STRING_FIXES = {
    "tab_server": "Servidor",
    "message_whois": "Informações do usuário %1$s",
    "message_mode_set": "definiu %1$s",
    "message_mode_unset": "removeu %1$s",
    "message_mode_gave": "deu %1$s",
    "message_mode_removed": "retirou %1$s",
    "message_mode_channel_value": "modo de canal \\'%1$s\\' para %2$s",
    "message_mode_channel_ban": "um ban em %1$s",
    "message_mode_nick_owner": "status de proprietário do canal",
    "message_mode_nick_admin": "status de administrador do canal",
    "message_mode_nick_op": "status de operador do canal",
    "message_mode_nick_half_op": "status de half-op do canal",
    "message_mode_nick_voice": "voice",
    "message_ctcp_version": "Solicitação CTCP VERSION recebida",
    "command_send_raw": "Enviar como comando raw",
    "channel_topic": "Tópico",
    "channel_members": "Membros",
    "action_mentions": "Menções",
    "mention_position": "Menção %1$d de %2$d",
    "unread_go_latest": "Ir para a mensagem mais recente e marcar como lida",
    "action_settings": "Configurações",
    "action_open": "Abrir",
    "network_catalog_search_hint": "Pesquisar redes",
    "network_catalog_sort_users": "Mais usuários",
    "network_catalog_approximately": "aproximadamente %1$s usuários · %2$s canais",
    "network_catalog_unavailable": "número de usuários indisponível",
    "network_catalog_plaintext": "conexão não criptografada",
    "action_copy": "Copiar",
    "action_clone": "Clonar",
    "action_finish": "Concluir",
    "action_search": "Pesquisar",
    "action_sort": "Ordenar",
    "action_message_user": "Enviar mensagem ao usuário",
    "action_reset": "Redefinir",
    "action_exit": "Sair",
    "action_reply": "Responder",
    "action_export": "Exportar",
    "action_import_theme": "Importar tema",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban no host",
    "operator_tban": "TBAN no host",
    "operator_mute": "Mute no host",
    "operator_audi": "Audi no ident",
    "operator_unban_host": "Remover ban exato do host",
    "operator_voice": "Dar voice",
    "operator_mask": "Máscara de ban",
    "operator_choose_channel": "Escolha o canal em que você é operador",
    "operator_not_authorised": "As ações de operador exigem status half-op ou superior neste canal.",
    "operator_reason_optional": "Motivo (opcional)",
    "operator_custom_reason": "Mensagem personalizada…",
    "operator_duration": "Duração (por exemplo, 3h)",
    "search_messages_title": "Pesquisar conversa",
    "search_messages_ready": "Pesquisar nas %1$d mensagens salvas mais recentes.",
    "search_messages_count": "Correspondência %1$d de %2$d",
    "format_italic": "Itálico",
    "format_underline": "Sublinhado",
    "service_title": "Conectado ao IRC",
}

PLURAL_FIXES = {
    "mention_counter": {
        "one": "1 menção",
        "other": "%1$d menções",
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

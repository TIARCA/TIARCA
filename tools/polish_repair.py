#!/usr/bin/env python3
from pathlib import Path
import re

PATH = Path(__file__).resolve().parents[1] / "app/src/main/res/values-pl/strings.xml"

STRING_FIXES = {
    "tab_server": "Serwer",
    "message_join": "%1$s dołączył do kanału",
    "message_part": "%1$s opuścił kanał (%2$s)",
    "message_part_no_message": "%1$s opuścił kanał",
    "message_whois": "Informacje o użytkowniku %1$s",
    "message_mode_set": "ustawił %1$s",
    "message_mode_unset": "usunął %1$s",
    "message_mode_removed": "odebrał %1$s",
    "message_mode_channel_value": "tryb kanału \\'%1$s\\' na %2$s",
    "message_mode_nick_owner": "status właściciela kanału",
    "message_mode_nick_admin": "status administratora kanału",
    "message_mode_nick_op": "status operatora kanału",
    "message_mode_nick_half_op": "status half-op kanału",
    "message_mode_nick_voice": "voice",
    "channel_members": "Użytkownicy",
    "action_mentions": "Wzmianki",
    "mention_position": "Wzmianka %1$d z %2$d",
    "action_expand": "Rozwiń",
    "action_settings": "Ustawienia",
    "action_reconnect": "Połącz ponownie",
    "action_open": "Otwórz",
    "action_back": "Wstecz",
    "network_catalog_sort_users": "Najwięcej użytkowników",
    "network_catalog_approximately": "około %1$s użytkowników · %2$s kanałów",
    "network_catalog_unavailable": "liczba użytkowników niedostępna",
    "action_copy": "Kopiuj",
    "action_clone": "Klonuj",
    "action_finish": "Zakończ",
    "action_sort": "Sortuj",
    "action_message_user": "Napisz do użytkownika",
    "action_exit": "Wyjdź",
    "action_reply": "Odpowiedz",
    "action_share": "Udostępnij",
    "action_export": "Eksportuj",
    "action_import_theme": "Importuj motyw",
    "operator_actions": "Akcje operatora",
    "operator_actions_for": "Akcje operatora w %1$s",
    "operator_kick": "Kick",
    "operator_kickban": "Kickban hosta",
    "operator_tban": "TBAN hosta",
    "operator_mute": "Mute hosta",
    "operator_audi": "Audi identu",
    "operator_unban_host": "Usuń dokładny ban hosta",
    "operator_voice": "Nadaj voice",
    "operator_mask": "Maska bana",
    "operator_historical_data": "Dane historyczne WHOWAS",
    "operator_historical_data_desc": "Host i ident pochodzą z ostatniego połączenia użytkownika.",
    "operator_choose_channel": "Wybierz kanał, na którym jesteś operatorem",
    "operator_not_authorised": "Akcje operatora wymagają na tym kanale statusu half-op lub wyższego.",
    "operator_custom_reason": "Wiadomość niestandardowa",
    "notification_channel_idle": "Powiadomienie o stanie",
    "notification_channel_warning": "Powiadomienia ostrzegawcze",
    "server_list_disconnected": "Rozłączony",
    "server_port": "Port",
    "server_encoding": "Kodowanie",
    "certificate_error": "Nie udało się zweryfikować certyfikatu %1$s",
    "certificate_error_footer": "Nie akceptuj tego certyfikatu, jeśli nie masz pewności, co to oznacza.",
    "title_activity_settings": "Ustawienia",
    "value_preset": "Ustawienie wstępne",
    "entry_name": "Nazwa",
    "pref_header_interface": "Interfejs",
    "pref_header_theme": "Motyw",
    "theme_category_chat_message": "Wiadomości",
    "theme_color_member_owner": "Właściciele",
    "theme_color_member_admin": "Administratorzy",
    "theme_color_member_op": "Operatorzy",
    "theme_color_member_voice": "Użytkownicy z voice",
    "theme_color_member_normal": "Zwykli użytkownicy",
    "pref_header_notifications": "Powiadomienia",
    "notification_header_match": "Dopasowanie",
    "notification_match_mode": "Tryb",
    "notification_rule_server": "Serwer",
    "notification_header_options": "Opcje",
    "edit_command_alias_type": "Typ",
    "pref_storage_configuration_title": "Konfiguracja",
    "pref_header_storage_limits": "Limity",
    "pref_header_backup": "Kopia zapasowa",
    "title_activity_backup": "Kopia zapasowa",
    "pref_value_default_part_message": "Opuszczanie",
    "message_format": "Format",
    "message_format_sender": "nadawca",
    "message_format_message": "wiadomość",
    "message_format_sender_prefix": "prefiks",
    "message_format_message_add": "Wiadomość",
    "user_server": "Serwer",
    "user_account": "Konto",
    "title_activity_dcc": "Transfery DCC",
    "dcc_summary_notification_title": "Trwające transfery DCC",
    "dcc_approve_download_body": "%1$s (z serwera: %2$s) wysłał Ci plik.\\n\\nOstrzeżenie: ten plik zostanie przesłany przez niezabezpieczone i nieszyfrowane połączenie. Zaakceptowanie pobierania pozwoli drugiej stronie zobaczyć Twój adres IP. Zaakceptuj tylko wtedy, gdy ufasz temu użytkownikowi.",
    "dcc_enable_send_warning_body": "DCC to starszy protokół udostępniania plików peer-to-peer, w którym pliki są przesyłane przez niezabezpieczone i nieszyfrowane połączenie. Oznacza to, że przesyłane dane mogą zostać skopiowane lub zmodyfikowane przez osoby atakujące, szczególnie w publicznych sieciach Wi-Fi.\\nPonadto, ze względu na sposób działania protokołu, udostępniając pliki przez DCC przekazujesz drugiej stronie swój adres IP. DCC zazwyczaj nie działa przy transmisji danych komórkowych i może nie działać w niektórych sieciach Wi-Fi.\\n\\nZ tego powodu zalecamy korzystanie z zewnętrznych usług udostępniania plików i przekazywanie docelowemu użytkownikowi wyłącznie linku zamiast używania DCC.",
    "dcc_download_dir_application": "Aplikacja",
    "dcc_download_dir_system": "System",
    "channel_mode_key_hint": "Klucz",
    "ban_list_menu": "Ban | Wyjątki",
    "ban_list_title": "Bany | Wyjątki dla %1$s",
    "ban_list_loading": "Ładowanie listy banów i wyjątków…",
    "ban_list_failed": "Nie można załadować listy banów i wyjątków.",
    "ban_list_empty": "Lista banów i wyjątków jest pusta.",
    "ban_list_select_one": "Wybierz co najmniej jeden ban lub wyjątek.",
    "ban_list_confirm_title": "Usunąć wybrane bany lub wyjątki?",
    "ban_list_cleanup": "Czyszczenie",
    "ban_list_cleanup_none": "Nie znaleziono banów kwalifikujących się do czyszczenia.",
    "ban_list_cleanup_confirm_title": "Wyczyścić tymczasowe bany?",
    "pref_operator_reasons_header": "Domyślne powody Kickban i TBAN",
    "pref_quick_commands_enabled_desc": "Przetwarzaj włączone polecenia ! lokalnie przed wysłaniem wyniku do bieżącego czatu",
    "pref_tmdb_key_hint": "Wbudowany klucz aplikacji; dotknij, aby użyć innego klucza",
    "media_viewer_title": "Multimedia",
    "media_open_external": "Otwórz zewnętrznie",
}

PLURAL_FIXES = {
    "mention_counter": {
        "one": "1 wzmianka", "few": "%1$d wzmianki", "many": "%1$d wzmianek", "other": "%1$d wzmianki",
    },
    "unread_message_counter": {
        "one": "1 nieprzeczytana wiadomość", "few": "%1$d nieprzeczytane wiadomości", "many": "%1$d nieprzeczytanych wiadomości", "other": "%1$d nieprzeczytanej wiadomości",
    },
    "notify_multiple_messages": {
        "one": "%1$d nieprzeczytana wiadomość", "few": "%1$d nieprzeczytane wiadomości", "many": "%1$d nieprzeczytanych wiadomości", "other": "%1$d nieprzeczytanej wiadomości",
    },
    "server_manage_custom_certs_text": {
        "one": "Zapisano %1$d niestandardowy certyfikat", "few": "Zapisano %1$d niestandardowe certyfikaty", "many": "Zapisano %1$d niestandardowych certyfikatów", "other": "Zapisano %1$d niestandardowego certyfikatu",
    },
    "channel_list_title_with_member_count": {
        "one": "%1$s (%2$d członek)", "few": "%1$s (%2$d członków)", "many": "%1$s (%2$d członków)", "other": "%1$s (%2$d członka)",
    },
    "time_seconds": {"one": "%1$d sekunda", "few": "%1$d sekundy", "many": "%1$d sekund", "other": "%1$d sekundy"},
    "time_minutes": {"one": "%1$d minuta", "few": "%1$d minuty", "many": "%1$d minut", "other": "%1$d minuty"},
    "time_hours": {"one": "%1$d godzina", "few": "%1$d godziny", "many": "%1$d godzin", "other": "%1$d godziny"},
    "time_days": {"one": "%1$d dzień", "few": "%1$d dni", "many": "%1$d dni", "other": "%1$d dnia"},
    "notification_rule_summary_multi_channels": {"one": "%1$d kanał", "few": "%1$d kanały", "many": "%1$d kanałów", "other": "%1$d kanału"},
    "notification_rule_summary_multi_nicks": {"one": "%1$d nick", "few": "%1$d nicki", "many": "%1$d nicków", "other": "%1$d nicku"},
    "reconnect_desc_tries": {"one": " (%1$d próba)", "few": " (%1$d próby)", "many": " (%1$d prób)", "other": " (%1$d próby)"},
    "dcc_summary_notification_text": {"one": "%1$d trwający transfer", "few": "%1$d trwające transfery", "many": "%1$d trwających transferów", "other": "%1$d trwającego transferu"},
    "ban_list_cleanup_confirm_message": {
        "one": "Wybrano %1$d ban kwalifikujący się do usunięcia. Wpisy j: i R:, nieznane daty oraz inne typy masek są pomijane.",
        "few": "Wybrano %1$d bany kwalifikujące się do usunięcia. Wpisy j: i R:, nieznane daty oraz inne typy masek są pomijane.",
        "many": "Wybrano %1$d banów kwalifikujących się do usunięcia. Wpisy j: i R:, nieznane daty oraz inne typy masek są pomijane.",
        "other": "Wybrano %1$d bana kwalifikującego się do usunięcia. Wpisy j: i R:, nieznane daty oraz inne typy masek są pomijane.",
    },
}

ARRAY_FIXES = {
    "edit_command_alias_types": ["Wiadomość", "Polecenie klienta", "Raw"],
}

UPSERT_AFTER = {
    "ban_list_date": [
        ("ban_list_search_hint", "Szukaj…"),
        ("ban_list_search_clear", "Wyczyść wyszukiwanie w liście banów"),
    ],
    "ban_list_empty": [
        ("ban_list_no_results", "Brak banów pasujących do wyszukiwania."),
    ],
    "ban_list_confirm_title": [
        ("ban_list_tab_bans", "Bany"),
        ("ban_list_tab_exceptions", "Wyjątki"),
    ],
    "ban_list_cleanup_none": [
        ("ban_list_exceptions_not_supported", "Ten serwer nie obsługuje wyjątków (+e)."),
    ],
}


def replace_string(text, name, value):
    pat = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.S)
    out, n = pat.subn(lambda m: m.group(1) + value + m.group(3), text, count=1)
    if n != 1:
        raise RuntimeError(f"Expected one string {name}, found {n}")
    return out


def upsert_string_after(text, anchor, name, value):
    existing = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.S)
    if existing.search(text):
        return existing.sub(lambda m: m.group(1) + value + m.group(3), text, count=1)
    anchor_pat = re.compile(r'(<string\s+name="' + re.escape(anchor) + r'"[^>]*>.*?</string>)', re.S)
    match = anchor_pat.search(text)
    if not match:
        raise RuntimeError(f"Missing insertion anchor {anchor}")
    new_line = f'\n    <string name="{name}">{value}</string>'
    return text[:match.end()] + new_line + text[match.end():]


def replace_plural(text, name, values):
    pat = re.compile(r'(<plurals\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</plurals>)', re.S)
    m = pat.search(text)
    if not m:
        raise RuntimeError(f"Missing plurals {name}")
    body = m.group(2)
    for quantity, value in values.items():
        ip = re.compile(r'(<item\s+quantity="' + re.escape(quantity) + r'"[^>]*>)(.*?)(</item>)', re.S)
        body, n = ip.subn(lambda x, v=value: x.group(1) + v + x.group(3), body, count=1)
        if n == 0:
            indent = "\n        "
            body = body.rstrip() + f'{indent}<item quantity="{quantity}">{value}</item>\n    '
        elif n != 1:
            raise RuntimeError(f"Expected at most one {name}/{quantity}, found {n}")
    return text[:m.start()] + m.group(1) + body + m.group(3) + text[m.end():]


def replace_array(text, name, values):
    pat = re.compile(r'(<string-array\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string-array>)', re.S)
    m = pat.search(text)
    if not m:
        raise RuntimeError(f"Missing array {name}")
    items = re.findall(r'<item[^>]*>.*?</item>', m.group(2), re.S)
    if len(items) != len(values):
        raise RuntimeError(f"Expected {len(values)} items in {name}, found {len(items)}")
    body = m.group(2)
    for old, value in zip(items, values):
        body = body.replace(old, re.sub(r'>.*?<', '>' + value + '<', old, count=1, flags=re.S), 1)
    return text[:m.start()] + m.group(1) + body + m.group(3) + text[m.end():]


def main():
    text = PATH.read_text(encoding="utf-8")
    for k, v in STRING_FIXES.items():
        text = replace_string(text, k, v)
    for anchor, entries in UPSERT_AFTER.items():
        for name, value in reversed(entries):
            text = upsert_string_after(text, anchor, name, value)
    for k, v in PLURAL_FIXES.items():
        text = replace_plural(text, k, v)
    for k, v in ARRAY_FIXES.items():
        text = replace_array(text, k, v)
    PATH.write_text(text, encoding="utf-8")
    inserted = sum(len(v) for v in UPSERT_AFTER.values())
    print(f"Updated Polish translations: {len(STRING_FIXES)} strings, {len(PLURAL_FIXES)} plurals, {len(ARRAY_FIXES)} arrays, {inserted} upserts")

if __name__ == "__main__":
    main()

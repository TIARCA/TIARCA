from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Expected source snippet not found in {path}")
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one source snippet in {path}, found {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "app/settings.yml",
    '''    - spec: boolean textAutocorrectEnabled = true\n      pref: chat_text_autocorrect\n\n    - spec: boolean hideJoinPartMessages = true\n''',
    '''    - spec: boolean textAutocorrectEnabled = true\n      pref: chat_text_autocorrect\n    - spec: String nickTapAction = "details"\n      enum: ["details", "insert_nick", "private"]\n\n    - spec: boolean hideJoinPartMessages = true\n''')

replace_once(
    "app/src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java",
    '''        a.add(new CheckBoxSetting(getString(R.string.pref_title_chat_box_always_multiline),\n                getString(R.string.pref_summary_chat_box_always_multiline))\n                .linkSetting(prefs, ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE));\n\n        MessageSenderInfo testSender = new MessageSenderInfo(\n''',
    '''        a.add(new CheckBoxSetting(getString(R.string.pref_title_chat_box_always_multiline),\n                getString(R.string.pref_summary_chat_box_always_multiline))\n                .linkSetting(prefs, ChatSettings.PREF_SEND_BOX_ALWAYS_MULTILINE));\n        a.add(new SettingsHeader(getString(R.string.pref_header_chat_interactions)));\n        a.add(new ListSetting(getString(R.string.pref_title_nick_tap_action),\n                getResources().getStringArray(R.array.pref_entries_nick_tap_action),\n                getResources().getStringArray(R.array.pref_entry_values_nick_tap_action))\n                .linkSetting(prefs, ChatSettings.PREF_NICK_TAP_ACTION));\n\n        MessageSenderInfo testSender = new MessageSenderInfo(\n''')

replace_once(
    "app/src/main/java/io/mrarm/irc/view/ChatAutoCompleteEditText.java",
    '''    public void requestTabComplete() {\n        performFiltering(true);\n    }\n\n    public void dismissDropDown() {\n''',
    '''    public void requestTabComplete() {\n        performFiltering(true);\n    }\n\n    /** Inserts a known nickname using the same token rules as TAB completion. */\n    public void insertCompletedNickname(String nick) {\n        if (nick == null || nick.trim().isEmpty())\n            return;\n        CharSequence value = terminateNickToken(nick.trim());\n        int start = findTokenStart();\n        int end = findTokenEnd();\n        clearComposingText();\n        getText().replace(start, end, value);\n        setSelection(Math.min(start + value.length(), getText().length()));\n        dismissDropDown();\n        requestFocus();\n    }\n\n    public void dismissDropDown() {\n''')

replace_once(
    "app/src/main/java/io/mrarm/irc/chat/ChatFragmentSendMessageHelper.java",
    '''    /** Inserts a channel mention without changing the current conversation or sending it. */\n    public void insertMention(String nick) {\n''',
    '''    /** Inserts a nickname exactly as if that nickname had been chosen by TAB completion. */\n    public void insertNicknameAsTabCompletion(String nick) {\n        mSendText.insertCompletedNickname(nick);\n    }\n\n    /** Inserts a channel mention without changing the current conversation or sending it. */\n    public void insertMention(String nick) {\n''')

adapter = Path("app/src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java")
text = adapter.read_text()
old_import = '''import io.mrarm.irc.NotificationManager;\nimport io.mrarm.irc.MainActivity;\nimport io.mrarm.irc.R;\n'''
new_import = '''import io.mrarm.irc.NotificationManager;\nimport io.mrarm.irc.MainActivity;\nimport io.mrarm.irc.R;\nimport io.mrarm.irc.config.ChatSettings;\nimport io.mrarm.irc.irc.CallerIdAcceptManager;\n'''
if old_import not in text:
    raise SystemExit("Expected ChatMessagesAdapter import block not found")
text = text.replace(old_import, new_import, 1)
old_click = '''                public void onClick(@NonNull View widget) {\n                    showUserDetails(widget, nick);\n                }\n'''
new_click = '''                public void onClick(@NonNull View widget) {\n                    handleNicknameTap(widget, nick);\n                }\n'''
if old_click not in text:
    raise SystemExit("Expected nickname click handler not found")
text = text.replace(old_click, new_click, 1)
old_details = '''        private void showUserDetails(View source, String nick) {\n            UserBottomSheetDialog dialog = new UserBottomSheetDialog(source.getContext());\n'''
new_details = '''        private void handleNicknameTap(View source, String nick) {\n            String action = ChatSettings.getNickTapAction();\n            if ("insert_nick".equals(action)) {\n                mFragment.getSendMessageHelper().insertNicknameAsTabCompletion(nick);\n                return;\n            }\n            if ("private".equals(action)) {\n                CallerIdAcceptManager.acceptOutgoingPrivateConversation(\n                        mFragment.getConnectionInfo(), nick);\n                if (source.getContext() instanceof MainActivity)\n                    ((MainActivity) source.getContext()).openDirectConversationForSharing(\n                            mFragment.getConnectionInfo(), nick);\n                else\n                    mFragment.getConnectionInfo().addStoredConversation(nick);\n                return;\n            }\n            showUserDetails(source, nick);\n        }\n\n        private void showUserDetails(View source, String nick) {\n            UserBottomSheetDialog dialog = new UserBottomSheetDialog(source.getContext());\n'''
if old_details not in text:
    raise SystemExit("Expected showUserDetails method not found")
text = text.replace(old_details, new_details, 1)
adapter.write_text(text)

resources = {
    "values": ("Chat interactions", "Nickname tap action", [
        "Show user information", "Insert nickname in message", "Open private conversation"]),
    "values-it": ("Interazioni nella chat", "Azione al tocco sul nickname", [
        "Mostra informazioni utente", "Inserisci nickname nel messaggio", "Apri conversazione privata"]),
    "values-de": ("Chat-Interaktionen", "Aktion beim Tippen auf Nickname", [
        "Benutzerinformationen anzeigen", "Nickname in Nachricht einfügen", "Privatgespräch öffnen"]),
    "values-es": ("Interacciones del chat", "Acción al tocar el apodo", [
        "Mostrar información del usuario", "Insertar apodo en el mensaje", "Abrir conversación privada"]),
    "values-fi": ("Keskustelun toiminnot", "Nimimerkin napautustoiminto", [
        "Näytä käyttäjätiedot", "Lisää nimimerkki viestiin", "Avaa yksityiskeskustelu"]),
    "values-fr": ("Interactions du chat", "Action lors d’un appui sur le pseudo", [
        "Afficher les informations utilisateur", "Insérer le pseudo dans le message", "Ouvrir une conversation privée"]),
    "values-pl": ("Interakcje na czacie", "Akcja po dotknięciu nicku", [
        "Pokaż informacje o użytkowniku", "Wstaw nick do wiadomości", "Otwórz rozmowę prywatną"]),
    "values-pt-rBR": ("Interações no chat", "Ação ao tocar no apelido", [
        "Mostrar informações do usuário", "Inserir apelido na mensagem", "Abrir conversa privada"]),
    "values-ro": ("Interacțiuni în chat", "Acțiune la atingerea pseudonimului", [
        "Afișează informațiile utilizatorului", "Inserează pseudonimul în mesaj", "Deschide conversația privată"]),
}
for dirname, (header, title, entries) in resources.items():
    p = Path("app/src/main/res") / dirname / "strings_tiarca_026.xml"
    p.parent.mkdir(parents=True, exist_ok=True)
    entry_xml = "\n".join(f"        <item>{x}</item>" for x in entries)
    extra = ""
    if dirname == "values":
        extra = '''\n    <string-array name="pref_entry_values_nick_tap_action" translatable="false">\n        <item>details</item>\n        <item>insert_nick</item>\n        <item>private</item>\n    </string-array>'''
    p.write_text(f'''<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="pref_header_chat_interactions">{header}</string>\n    <string name="pref_title_nick_tap_action">{title}</string>\n    <string-array name="pref_entries_nick_tap_action">\n{entry_xml}\n    </string-array>{extra}\n</resources>\n''')

test = Path("app/src/test/java/io/mrarm/irc/NicknameTapActionStructureTest.java")
test.write_text(r'''package io.mrarm.irc;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression coverage for TIARCA-026 nickname tap behavior and settings wiring. */
public class NicknameTapActionStructureTest {

    @Test
    public void defaultKeepsExistingUserDetailsBehavior() throws IOException {
        String settings = read("app/settings.yml", "settings.yml");
        assertTrue(settings.contains("String nickTapAction = \"details\""));
        assertTrue(settings.contains("enum: [\"details\", \"insert_nick\", \"private\"]"));
    }

    @Test
    public void interfaceExposesChatInteractionChoice() throws IOException {
        String source = read(
                "app/src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java",
                "src/main/java/io/mrarm/irc/setting/fragment/InterfaceSettingsFragment.java");
        assertTrue(source.contains("pref_header_chat_interactions"));
        assertTrue(source.contains("PREF_NICK_TAP_ACTION"));
    }

    @Test
    public void nicknameTapCanReuseTabCompletionSemantics() throws IOException {
        String editText = read(
                "app/src/main/java/io/mrarm/irc/view/ChatAutoCompleteEditText.java",
                "src/main/java/io/mrarm/irc/view/ChatAutoCompleteEditText.java");
        assertTrue(editText.contains("terminateNickToken(nick.trim())"));
        assertTrue(editText.contains("getText().replace(start, end, value)"));

        String adapter = read(
                "app/src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java",
                "src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java");
        assertTrue(adapter.contains("handleNicknameTap(widget, nick)"));
        assertTrue(adapter.contains("insertNicknameAsTabCompletion(nick)"));
        assertTrue(adapter.contains("openDirectConversationForSharing"));
    }

    private static String read(String rootPath, String modulePath) throws IOException {
        Path path = Paths.get(rootPath);
        if (!Files.exists(path))
            path = Paths.get(modulePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
''')

from pathlib import Path

p = Path("app/src/main/java/io/mrarm/irc/chat/ChatMessagesAdapter.java")
text = p.read_text()
old = '''            if ("insert_nick".equals(action)) {\n                mFragment.getSendMessageHelper().insertNicknameAsTabCompletion(nick);\n                return;\n            }\n'''
new = '''            if ("insert_nick".equals(action)) {\n                if (mFragment.getParentFragment() instanceof ChatFragment) {\n                    ChatFragment parent = (ChatFragment) mFragment.getParentFragment();\n                    if (parent.getSendMessageHelper() != null)\n                        parent.getSendMessageHelper().insertNicknameAsTabCompletion(nick);\n                }\n                return;\n            }\n'''
if text.count(old) != 1:
    raise SystemExit(f"Expected exactly one insertion handler, found {text.count(old)}")
p.write_text(text.replace(old, new, 1))

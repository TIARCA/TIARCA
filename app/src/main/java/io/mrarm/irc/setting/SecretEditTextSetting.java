package io.mrarm.irc.setting;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import io.mrarm.irc.R;

/** Edit-text preference whose value is never rendered in the settings list. */
public class SecretEditTextSetting extends SimpleSetting {

    private static final int sHolder = SettingsListAdapter.registerViewHolder(Holder.class,
            R.layout.settings_list_entry);
    private String mText;
    private final String mEmptyDescription;

    public SecretEditTextSetting(String name, String emptyDescription) {
        super(name, emptyDescription);
        mText = "";
        mEmptyDescription = emptyDescription;
    }

    public SecretEditTextSetting linkPreference(SharedPreferences prefs, String pref) {
        mText = prefs.getString(pref, "");
        setAssociatedPreference(prefs, pref);
        updateDescription();
        return this;
    }

    private void setText(String value) {
        mText = value == null ? "" : value.trim();
        if (hasAssociatedPreference()) mPreferences.edit().putString(mPreferenceName, mText).apply();
        updateDescription();
    }

    private void updateDescription() {
        mValue = mText.isEmpty() ? mEmptyDescription : "••••••••";
        onUpdated();
    }

    @Override public int getViewHolder() { return sHolder; }

    public static class Holder extends SimpleSetting.Holder<SecretEditTextSetting> {
        public Holder(View itemView, SettingsListAdapter adapter) { super(itemView, adapter); }

        @Override public void onClick(View v) {
            SecretEditTextSetting entry = getEntry();
            View view = LayoutInflater.from(v.getContext()).inflate(R.layout.dialog_edit_text, null);
            EditText text = view.findViewById(R.id.edit_text);
            text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            text.setText(entry.mText);
            text.setSelection(text.length());
            new AlertDialog.Builder(v.getContext()).setTitle(entry.mName).setView(view)
                    .setPositiveButton(R.string.action_ok,
                            (DialogInterface dialog, int which) -> entry.setText(text.getText().toString()))
                    .setNegativeButton(R.string.action_cancel, null).show();
        }
    }
}

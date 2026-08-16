package io.mrarm.irc.config;

import android.content.Context;
import android.content.SharedPreferences;
import io.mrarm.irc.util.DefaultPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Up to five editable reasons shown by Kickban and TBAN dialogs. */
public final class OperatorReasonSettings {

    public static final int MAX_REASONS = 5;
    public static final String[] PREF_KEYS = {
            "operator_reason_1", "operator_reason_2", "operator_reason_3",
            "operator_reason_4", "operator_reason_5"
    };
    public static final String[] DEFAULT_REASONS = {
            "no hot", "non ripetere", "no annunci", "no disagio", ""
    };

    private OperatorReasonSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return DefaultPreferences.get(context.getApplicationContext());
    }

    public static List<String> getReasons(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> unique = new LinkedHashSet<>();
        for (int i = 0; i < MAX_REASONS; i++) {
            String value = preferences.getString(PREF_KEYS[i], DEFAULT_REASONS[i]);
            if (value == null)
                continue;
            value = value.trim().replace('\r', ' ').replace('\n', ' ');
            if (!value.isEmpty())
                unique.add(value);
        }
        return new ArrayList<>(unique);
    }
}

package io.mrarm.irc.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.mrarm.irc.MainActivity;
import io.mrarm.irc.ThemedActivity;

/** Lightweight launcher that decides whether a genuine fresh install needs onboarding. */
public class EntryActivity extends ThemedActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent next;
        if (OnboardingState.shouldAutoLaunch(this)) {
            next = OnboardingActivity.createIntent(this, false);
        } else {
            next = new Intent(this, MainActivity.class);
        }
        startActivity(next);
        finish();
    }
}

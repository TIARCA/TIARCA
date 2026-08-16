package io.mrarm.irc.setup;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AppCompatActivity;

import io.mrarm.irc.R;

public class SetupActivity extends AppCompatActivity {

    public static final int RESULT_CODE_FINISHED = 10001;
    private ActivityResultLauncher<Intent> mNextStepLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNextStepLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_CODE_FINISHED)
                        setSetupFinished();
                });
    }

    @Override
    public void finish() {
        super.finish();
        setSlideAnimation(true);
    }

    protected void setSlideAnimation(boolean fromRight) {
        if (ViewCompat.getLayoutDirection(getWindow().getDecorView())
                == ViewCompat.LAYOUT_DIRECTION_RTL)
            fromRight = !fromRight;

        if (fromRight)
            overridePendingTransition(R.anim.slide_rtl_enter, R.anim.slide_rtl_exit);
        else
            overridePendingTransition(R.anim.slide_enter, R.anim.slide_exit);
    }

    public void setSetupFinished() {
        setResult(RESULT_CODE_FINISHED);
        finish();
    }

    public void startNextActivity(Intent intent) {
        mNextStepLauncher.launch(intent);
        setSlideAnimation(false);
    }

}

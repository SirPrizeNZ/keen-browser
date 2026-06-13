package com.keen.tv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String BRAVE_PACKAGE = "com.brave.browser_nightly";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(BRAVE_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, "Keen Browser is not installed", Toast.LENGTH_LONG).show();
        } else {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
        finish();
    }
}

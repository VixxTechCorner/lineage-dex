package dev.lineagedex;

import android.app.Activity;
import android.os.Bundle;

/** Routes launcher and scrcpy launches to the desktop for the actual display. */
public class LaunchActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Displays.open(this,Displays.currentId(this));
        finish();
    }
}

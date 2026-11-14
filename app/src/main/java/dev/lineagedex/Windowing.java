package dev.lineagedex;

import android.app.ActivityOptions;
import android.os.Build;
import android.os.Bundle;

final class Windowing {
    static final int FULLSCREEN=1, FREEFORM=5;
    static Bundle bundle(ActivityOptions options,int requestedMode) {
        Bundle result=options.toBundle();
        // Android 10–13 compatibility path from AOSP ActivityOptions. Bounds alone
        // do not select freeform mode. This internal key is not a cross-version API.
        if(Build.VERSION.SDK_INT<=33 && requestedMode!=0)
            result.putInt("android.activity.windowingMode",requestedMode);
        return result;
    }
}

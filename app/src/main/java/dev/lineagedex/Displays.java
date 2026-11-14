package dev.lineagedex;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

final class Displays {
    static int currentId(Context context) {
        try {
            Display display=android.os.Build.VERSION.SDK_INT>=30?context.getDisplay():context.getSystemService(android.view.WindowManager.class).getDefaultDisplay();
            return display==null?Display.DEFAULT_DISPLAY:display.getDisplayId();
        } catch (UnsupportedOperationException e) { return Display.DEFAULT_DISPLAY; }
    }
    static Intent desktopIntent(Context context,int id) {
        return new Intent(context,id==Display.DEFAULT_DISPLAY?DesktopActivity.class:ExternalDesktopActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    static List<Display> available(Context context) {
        List<Display> result=new ArrayList<>();
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) return result;
        for(Display display:context.getSystemService(DisplayManager.class).getDisplays()) {
            int id=display.getDisplayId();
            if(id==Display.DEFAULT_DISPLAY || !display.isValid() || display.getState()==Display.STATE_OFF) continue;
            if(context.getSystemService(ActivityManager.class).isActivityStartAllowedOnDisplay(context,id,desktopIntent(context,id))) result.add(display);
        }
        return result;
    }
    static void open(Context context,int id) {
        Display display=context.getSystemService(DisplayManager.class).getDisplay(id);
        if(display==null || !display.isValid()) {
            Toast.makeText(context,"That display was disconnected",Toast.LENGTH_LONG).show(); return;
        }
        Intent intent=desktopIntent(context,id);
        if(!context.getSystemService(ActivityManager.class).isActivityStartAllowedOnDisplay(context,id,intent)) {
            Toast.makeText(context,"Android does not allow launching on that display",Toast.LENGTH_LONG).show(); return;
        }
        try { context.startActivity(intent,Windowing.bundle(ActivityOptions.makeBasic().setLaunchDisplayId(id),Windowing.FULLSCREEN)); }
        catch(RuntimeException e) { Toast.makeText(context,"Could not open the monitor desktop",Toast.LENGTH_LONG).show(); }
    }
}

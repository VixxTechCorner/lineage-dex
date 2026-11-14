package dev.lineagedex;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Apps {
    enum Mode { DEFAULT, FLOATING, LEFT, RIGHT, ADJACENT }
    private static int windowSequence=0;
    static final class Entry {
        final ComponentName component; final String label; final Drawable icon;
        Entry(ComponentName c, String l, Drawable i) { component=c; label=l; icon=i; }
        String key() { return component.flattenToString(); }
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("desktop",Context.MODE_PRIVATE); }
    static List<Entry> load(Context c) {
        PackageManager pm=c.getPackageManager(); List<Entry> result=new ArrayList<>();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen=new LinkedHashSet<>();
        for (ResolveInfo r:pm.queryIntentActivities(query,0)) {
            if (r.activityInfo.packageName.equals(c.getPackageName())) continue;
            ComponentName component=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
            if (!seen.add(component.flattenToString())) continue;
            try { result.add(new Entry(component,r.loadLabel(pm).toString(),r.loadIcon(pm))); }
            catch (RuntimeException ignored) { /* App may have been uninstalled during discovery. */ }
        }
        result.sort(Comparator.comparing(e -> e.label,String.CASE_INSENSITIVE_ORDER)); return result;
    }
    static List<Entry> pinned(Context c,List<Entry> entries) {
        String saved=prefs(c).getString("pins",null); List<Entry> result=new ArrayList<>();
        if (saved==null) return new ArrayList<>(entries.subList(0,Math.min(5,entries.size())));
        for (String key:saved.split("\\n")) for (Entry e:entries) if (e.key().equals(key)) result.add(e);
        return result;
    }
    static void togglePin(Context c,Entry entry,List<Entry> all) {
        List<Entry> pins=pinned(c,all); boolean removed=pins.removeIf(e -> e.key().equals(entry.key()));
        if (!removed) {
            if (pins.size()>=8) { Toast.makeText(c,"The desktop holds up to 8 shortcuts",Toast.LENGTH_SHORT).show(); return; }
            pins.add(entry);
        }
        List<String> keys=new ArrayList<>(); for (Entry e:pins) keys.add(e.key());
        prefs(c).edit().putString("pins",String.join("\n",keys)).apply();
        Toast.makeText(c,removed?"Shortcut removed":"Added to desktop",Toast.LENGTH_SHORT).show();
    }
    static void launch(Context c,Entry e) {
        boolean monitor=Displays.currentId(c)!=android.view.Display.DEFAULT_DISPLAY;
        launch(c,e,(monitor || prefs(c).getBoolean("windows",false))?Mode.FLOATING:Mode.DEFAULT);
    }
    private static Intent intent(Entry e,Mode mode) {
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(e.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if(mode==Mode.ADJACENT) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        return intent;
    }
    private static android.os.Bundle options(Context c,Mode mode) {
        ActivityOptions options=ActivityOptions.makeBasic();
        options.setLaunchDisplayId(Displays.currentId(c));
        if(mode==Mode.FLOATING || mode==Mode.LEFT || mode==Mode.RIGHT) {
            android.util.DisplayMetrics metrics=c.getResources().getDisplayMetrics();
            WindowPlacement.Layout layout=mode==Mode.LEFT?WindowPlacement.Layout.LEFT:
                mode==Mode.RIGHT?WindowPlacement.Layout.RIGHT:WindowPlacement.Layout.FLOATING;
            int[] b=WindowPlacement.bounds(metrics.widthPixels,metrics.heightPixels,metrics.density,layout,windowSequence++);
            options.setLaunchBounds(new Rect(b[0],b[1],b[2],b[3]));
        }
        boolean floating=mode==Mode.FLOATING || mode==Mode.LEFT || mode==Mode.RIGHT;
        return Windowing.bundle(options,floating?Windowing.FREEFORM:mode==Mode.DEFAULT?Windowing.FULLSCREEN:0);
    }
    private static boolean allowed(Context c,Intent intent) {
        int displayId=Displays.currentId(c);
        return displayId==android.view.Display.DEFAULT_DISPLAY ||
            c.getSystemService(android.app.ActivityManager.class).isActivityStartAllowedOnDisplay(c,displayId,intent);
    }
    static void launch(Context c,Entry e,Mode mode) {
        if(mode==Mode.ADJACENT && android.os.Build.VERSION.SDK_INT<32) {
            Toast.makeText(c,"Enter split screen from Android Recents first",Toast.LENGTH_LONG).show(); return;
        }
        Intent intent=intent(e,mode);
        try {
            if(!allowed(c,intent)) {
                Toast.makeText(c,"Android does not allow this app on the monitor",Toast.LENGTH_LONG).show(); return;
            }
            c.startActivity(intent,options(c,mode));
            remember(c,e);
        } catch (RuntimeException ex) {
            android.util.Log.e("LineageDeX","App launch failed: "+e.key()+" mode="+mode,ex);
            Toast.makeText(c,"Cannot open "+e.label+". Try Open normally, or check the multitasking setup.",Toast.LENGTH_LONG).show();
        }
    }
    static List<Entry> recent(Context c,List<Entry> all) {
        List<Entry> result=new ArrayList<>();
        for(String key:prefs(c).getString("recent_launches","").split("\n"))
            for(Entry e:all) if(e.key().equals(key)) { result.add(e); break; }
        return result;
    }
    private static void remember(Context c,Entry entry) {
        LinkedHashSet<String> keys=new LinkedHashSet<>(); keys.add(entry.key());
        for(String key:prefs(c).getString("recent_launches","").split("\n")) {
            if(!key.isEmpty()) keys.add(key);
            if(keys.size()>=12) break;
        }
        prefs(c).edit().putString("recent_launches",String.join("\n",keys)).apply();
    }
    static void launchPair(Context c,Entry first,Entry second,boolean splitScreen) {
        if(first.key().equals(second.key())) {
            Toast.makeText(c,"Choose two different apps",Toast.LENGTH_LONG).show(); return;
        }
        if(splitScreen && android.os.Build.VERSION.SDK_INT<32) {
            Toast.makeText(c,"Use Android Recents to enter split screen on this version",Toast.LENGTH_LONG).show(); return;
        }
        try {
            Intent a=intent(first,Mode.DEFAULT),b=intent(second,splitScreen?Mode.ADJACENT:Mode.DEFAULT);
            if(!allowed(c,a) || !allowed(c,b)) {
                Toast.makeText(c,"Android does not allow both apps on this display",Toast.LENGTH_LONG).show(); return;
            }
            if(splitScreen) {
                // Android 12L+ may place the second task beside the first.
                // This is a request, not a privileged task-organizer operation.
                ActivityOptions pairOptions=ActivityOptions.makeBasic().setLaunchDisplayId(Displays.currentId(c));
                c.startActivities(new Intent[]{a,b},pairOptions.toBundle());
                remember(c,first); remember(c,second);
            } else {
                launch(c,first,Mode.LEFT); launch(c,second,Mode.RIGHT);
            }
        } catch (RuntimeException ex) {
            android.util.Log.e("LineageDeX","App pair launch failed",ex);
            Toast.makeText(c,"Could not open the app pair. Use Android Recents to arrange the apps.",Toast.LENGTH_LONG).show();
        }
    }
    static void showActions(Context c,Entry e,List<Entry> all,Runnable changed) {
        boolean pinned=pinned(c,all).stream().anyMatch(p -> p.key().equals(e.key()));
        String[] actions={"Open normally","Floating window","Left half","Right half",
            "Split screen beside desktop",pinned?"Remove desktop shortcut":"Add desktop shortcut"};
        new android.app.AlertDialog.Builder(c).setTitle(e.label).setItems(actions,(dialog,index) -> {
            if(index==5) { togglePin(c,e,all); changed.run(); return; }
            changed.run();
            Mode[] modes={Mode.DEFAULT,Mode.FLOATING,Mode.LEFT,Mode.RIGHT,Mode.ADJACENT};
            launch(c,e,modes[index]);
        }).setNegativeButton("Cancel",null).show();
    }
}

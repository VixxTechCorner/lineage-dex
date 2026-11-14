package dev.lineagedex;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class DesktopActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private List<Apps.Entry> apps=new ArrayList<>();
    private GridLayout shortcuts;
    private TextView clock;
    private TextView monitorStatus;
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {
            updateMonitorStatus();
            if(Displays.currentId(DesktopActivity.this)==Display.DEFAULT_DISPLAY && Apps.prefs(DesktopActivity.this).getBoolean("auto_monitor",false)) {
                for(Display display:Displays.available(DesktopActivity.this))
                    if(display.getDisplayId()==id) Displays.open(DesktopActivity.this,id);
            }
        }
        public void onDisplayChanged(int id) { updateMonitorStatus(); }
        public void onDisplayRemoved(int id) {
            if(DesktopActivity.this instanceof ExternalDesktopActivity && Displays.currentId(DesktopActivity.this)==id) finishAndRemoveTask();
            else updateMonitorStatus();
        }
    };
    private final Runnable tick=new Runnable() {
        public void run() {
            if(clock!=null) clock.setText(android.text.format.DateFormat.format("EEE, dd MMM   ·   HH:mm",new Date()));
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Retire the old app overlay; the system owns navigation and taskbar UI.
        stopService(new Intent(this,DockService.class));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.rgb(19,27,48));
        getWindow().setNavigationBarColor(Color.rgb(19,27,48));
        FrameLayout root=new FrameLayout(this); root.addView(new Wallpaper(this));
        LinearLayout page=Ui.column(this);
        int margin=Ui.dp(this,24);
        page.setPadding(margin,Ui.dp(this,12),margin,Ui.dp(this,20));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=Ui.button(this,"Apps",this::showApps);
        menu.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_apps,0,0,0);
        menu.setCompoundDrawablePadding(Ui.dp(this,10)); top.addView(menu);
        TextView multitask=Ui.button(this,"Multitask",this::showMultitasking);
        LinearLayout.LayoutParams multitaskParams=new LinearLayout.LayoutParams(-2,-2);
        multitaskParams.setMarginStart(Ui.dp(this,10)); top.addView(multitask,multitaskParams);
        clock=Ui.text(this,"",13,Ui.TEXT); clock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        clock.setSingleLine(true); clock.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(clock,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView settings=Ui.button(this,"Settings",this::showSettings);
        LinearLayout.LayoutParams settingsParams=new LinearLayout.LayoutParams(-2,-2);
        settingsParams.setMarginStart(Ui.dp(this,20)); top.addView(settings,settingsParams);
        page.addView(top);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout workspace=Ui.column(this); workspace.setPadding(0,Ui.dp(this,32),0,0);
        shortcuts=new GridLayout(this);
        shortcuts.setColumnCount(Math.max(2,Math.min(4,getResources().getConfiguration().screenWidthDp/130)));
        workspace.addView(shortcuts,new LinearLayout.LayoutParams(-2,-2));
        workspace.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        monitorStatus=Ui.text(this,"",12,Ui.MUTED); monitorStatus.setGravity(Gravity.END);
        workspace.addView(monitorStatus,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(workspace); page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(page,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        // On Android 13 PhoneWindow has no DecorView before content is installed.
        // Posting on the root also waits for attachment before requesting insets.
        if(Build.VERSION.SDK_INT>=30) root.post(() -> {
            WindowInsetsController controller=root.getWindowInsetsController();
            if(controller!=null) controller.show(WindowInsets.Type.systemBars());
        });
        root.setOnLongClickListener(v -> { showSettings(); return true; });
    }
    @Override protected void onResume() {
        super.onResume(); handler.removeCallbacks(tick); tick.run();
        updateMonitorStatus();
        worker.execute(() -> { List<Apps.Entry> loaded=Apps.load(getApplicationContext());
            runOnUiThread(() -> { if (!isDestroyed()) { apps=loaded; renderShortcuts(); } }); });
    }
    @Override protected void onStart() {
        super.onStart(); getSystemService(DisplayManager.class).registerDisplayListener(displayListener,handler);
    }
    @Override protected void onStop() {
        getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener); super.onStop();
    }
    private void updateMonitorStatus() {
        if(monitorStatus==null) return;
        int id=Displays.currentId(this);
        if(id!=Display.DEFAULT_DISPLAY) monitorStatus.setText("Monitor workspace");
        else {
            int count=Displays.available(this).size();
            monitorStatus.setText(count==0?"":"Monitor connected");
        }
    }
    private void showMonitors() {
        List<Display> displays=Displays.available(this);
        if(displays.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Connect a monitor")
                .setMessage("For this tablet, use the Lineage DeX USB or Wi-Fi script on your Arch PC. It creates a separate Android display. A charging cable directly to a monitor cannot provide video. Wireless mirroring alone may not create an independent display.")
                .setPositiveButton("OK",null).show(); return;
        }
        String[] labels=new String[displays.size()];
        for(int i=0;i<labels.length;i++) labels[i]=displays.get(i).getName()+" · display "+displays.get(i).getDisplayId();
        new AlertDialog.Builder(this).setTitle("Open desktop on")
            .setItems(labels,(dialog,index) -> Displays.open(this,displays.get(index).getDisplayId()))
            .setNegativeButton("Cancel",null).show();
    }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }
    private void renderShortcuts() {
        shortcuts.removeAllViews();
        for(Apps.Entry e:Apps.pinned(this,apps)) {
            LinearLayout tile=Ui.column(this); tile.setGravity(Gravity.CENTER);
            tile.setPadding(Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12));
            tile.setBackground(Ui.selectable(this)); tile.setFocusable(true);
            ImageView icon=new ImageView(this); icon.setImageDrawable(e.icon);
            tile.addView(icon,new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
            TextView label=Ui.text(this,e.label,13,Ui.TEXT); label.setGravity(Gravity.CENTER);
            label.setMaxLines(2); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setShadowLayer(3,0,1,Color.argb(150,0,0,0));
            LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,Ui.dp(this,38));
            labelParams.topMargin=Ui.dp(this,8); tile.addView(label,labelParams);
            tile.setContentDescription(e.label);
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();
            params.width=Ui.dp(this,104); params.height=Ui.dp(this,122);
            params.setMargins(0,0,Ui.dp(this,12),Ui.dp(this,12)); shortcuts.addView(tile,params);
            tile.setOnClickListener(v -> Apps.launch(this,e));
            tile.setOnLongClickListener(v -> { Apps.showActions(this,e,apps,this::renderShortcuts); return true; });
        }
    }
    private void showMultitasking() {
        String[] actions={"Open two apps side by side","Recent launches","Clear recent launches","Multitasking help"};
        new AlertDialog.Builder(this).setTitle("Multitasking").setItems(actions,(dialog,index) -> {
            if(index==0) new AlertDialog.Builder(this).setTitle("Choose a layout")
                .setItems(new String[]{"Side-by-side windows","Android split screen (ROM dependent)"},
                    (layout,choice) -> choosePairFirst(choice==1))
                .setNegativeButton("Cancel",null).show();
            else if(index==1) showRecent();
            else if(index==2) Apps.prefs(this).edit().remove("recent_launches").apply();
            else new AlertDialog.Builder(this).setTitle("Work with multiple apps")
                .setMessage("Choose two apps to request a split-screen layout or floating halves. Hold any app icon for individual window options. Floating windows require freeform support in Developer options and a reboot.\n\nAndroid decides whether each app can use the requested layout. Existing app windows may keep their previous position. Use the original taskbar or Android Recents to switch, resize, or close windows.\n\nRecent launches lists apps opened through Lineage DeX, not every running app.\n\nKeyboard shortcuts while the desktop has focus: Ctrl+Tab for recent launches, Ctrl+Space for apps, Ctrl+M for multitasking.")
                .setPositiveButton("OK",null).show();
        }).setNegativeButton("Close",null).show();
    }
    private void showRecent() {
        List<Apps.Entry> recent=Apps.recent(this,apps);
        if(recent.isEmpty()) {
            Toast.makeText(this,"Apps opened through Lineage DeX will appear here",Toast.LENGTH_LONG).show(); return;
        }
        String[] names=new String[recent.size()];
        for(int i=0;i<names.length;i++) names[i]=recent.get(i).label;
        new AlertDialog.Builder(this).setTitle("Recent launches").setItems(names,
            (dialog,index) -> Apps.launch(this,recent.get(index)))
            .setNegativeButton("Cancel",null).show();
    }
    private void choosePairFirst(boolean splitScreen) {
        if(apps.size()<2) {
            Toast.makeText(this,"Install at least two apps to create an app pair",Toast.LENGTH_LONG).show(); return;
        }
        List<Apps.Entry> choices=new ArrayList<>(apps);
        String[] names=new String[choices.size()];
        for(int i=0;i<names.length;i++) names[i]=choices.get(i).label;
        new AlertDialog.Builder(this).setTitle("Choose the first app").setItems(names,(dialog,index) -> {
            Apps.Entry first=choices.get(index);
            List<Apps.Entry> second=new ArrayList<>(choices); second.remove(index);
            String[] secondNames=new String[second.size()];
            for(int i=0;i<secondNames.length;i++) secondNames[i]=second.get(i).label;
            new AlertDialog.Builder(this).setTitle("Open beside "+first.label)
                .setItems(secondNames,(picker,selected) -> Apps.launchPair(this,first,second.get(selected),splitScreen))
                .setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Cancel",null).show();
    }
    @Override public boolean onKeyShortcut(int keyCode,KeyEvent event) {
        if(event.isCtrlPressed() && !event.isAltPressed()) {
            if(keyCode==KeyEvent.KEYCODE_TAB) { showRecent(); return true; }
            if(keyCode==KeyEvent.KEYCODE_SPACE) { showApps(); return true; }
            if(keyCode==KeyEvent.KEYCODE_M) { showMultitasking(); return true; }
        }
        return super.onKeyShortcut(keyCode,event);
    }
    private void showApps() {
        Dialog dialog=new Dialog(this);
        AppMenu menu=new AppMenu(this,apps,() -> { renderShortcuts(); dialog.dismiss(); });
        menu.setPadding(Ui.dp(this,20),Ui.dp(this,16),Ui.dp(this,20),Ui.dp(this,12));
        menu.setBackground(Ui.bg(Color.rgb(25,33,53),Ui.dp(this,24)));
        dialog.setContentView(menu); dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(.22f);
            window.setGravity(Gravity.TOP|Gravity.START);
            WindowManager.LayoutParams params=window.getAttributes();
            params.x=Ui.dp(this,16); params.y=Ui.dp(this,68); window.setAttributes(params);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-Ui.dp(this,32),Ui.dp(this,640)),
                Math.min(getResources().getDisplayMetrics().heightPixels-Ui.dp(this,112),Ui.dp(this,520)));
        }
    }
    private void showSettings() {
        LinearLayout content=Ui.column(this); content.setPadding(24,16,24,16);
        Switch auto=new Switch(this); auto.setText("Open desktop when a monitor connects (while app is visible)");
        auto.setChecked(Apps.prefs(this).getBoolean("auto_monitor",false));
        auto.setOnCheckedChangeListener((v,on) -> Apps.prefs(this).edit().putBoolean("auto_monitor",on).apply()); content.addView(auto);
        Switch windows=new Switch(this); windows.setText("Open tablet apps in floating windows");
        windows.setChecked(Apps.prefs(this).getBoolean("windows",false));
        windows.setOnCheckedChangeListener((v,on) -> Apps.prefs(this).edit().putBoolean("windows",on).apply()); content.addView(windows);
        content.addView(Ui.text(this,"Enable freeform windows, force desktop mode and force activities to be resizable in Developer options, then reboot. Monitor apps request floating windows automatically. Your ROM still controls window support.",14,Ui.MUTED));
        content.addView(Ui.button(this,"Developer options",() -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));
        content.addView(Ui.text(this,"Use the LineageOS system launcher as your Home app to retain its taskbar. This desktop keeps Android navigation visible; it does not draw a replacement dock.",14,Ui.MUTED));
        content.addView(Ui.button(this,"Home app settings",() -> open(new Intent(Settings.ACTION_HOME_SETTINGS))));
        content.addView(Ui.button(this,"Connect a monitor",this::showMonitors));
        ScrollView scroll=new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Desktop settings").setView(scroll).setPositiveButton("Done",null).show();
    }
    private void open(Intent intent) {
        try { startActivity(intent); } catch(ActivityNotFoundException ex) { Toast.makeText(this,"This settings page is unavailable",Toast.LENGTH_SHORT).show(); }
    }
    private static final class Wallpaper extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Wallpaper(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            float w=getWidth(),h=getHeight();
            paint.setShader(new LinearGradient(0,0,w,h,
                new int[]{Color.rgb(20,31,53),Color.rgb(38,53,82),Color.rgb(70,69,108)},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);
            paint.setShader(new LinearGradient(w*.35f,h*.2f,w,h,
                new int[]{Color.rgb(93,121,159),Color.rgb(55,68,111),Color.rgb(32,40,76)},null,Shader.TileMode.CLAMP));
            Path ribbon=new Path(); ribbon.moveTo(w*.38f,h); ribbon.cubicTo(w*.76f,h*.65f,w*.33f,h*.12f,w*.91f,-h*.15f);
            ribbon.lineTo(w*1.15f,-h*.15f); ribbon.lineTo(w*1.15f,h*1.15f); ribbon.close();
            canvas.drawPath(ribbon,paint);
            paint.setShader(new LinearGradient(w*.6f,h*.2f,w,h,
                Color.argb(75,197,191,239),Color.argb(0,116,162,200),Shader.TileMode.CLAMP));
            Path fold=new Path(); fold.moveTo(w*.62f,h); fold.cubicTo(w*.99f,h*.65f,w*.53f,h*.24f,w*1.02f,-h*.1f);
            fold.lineTo(w*1.1f,h*1.1f); fold.close(); canvas.drawPath(fold,paint); paint.setShader(null);

        }
    }
}

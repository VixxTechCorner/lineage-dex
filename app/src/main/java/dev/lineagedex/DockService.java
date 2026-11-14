package dev.lineagedex;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class DockService extends Service {
    private WindowManager manager;
    private Context displayContext;
    private int displayId=Display.DEFAULT_DISPLAY;
    private boolean appsReady=false;
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {}
        public void onDisplayChanged(int id) {}
        public void onDisplayRemoved(int id) { if(id==displayId) stopSelf(); }
    };
    private LinearLayout root;
    private List<Apps.Entry> apps=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean expanded=false, compact=false, destroyed=false;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("dock","Desktop dock",NotificationManager.IMPORTANCE_LOW));
        PendingIntent home=PendingIntent.getActivity(this,0,new Intent(this,DesktopActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,DockService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(1,new Notification.Builder(this,"dock").setSmallIcon(dev.lineagedex.R.drawable.ic_desktop)
            .setContentTitle("Lineage DeX dock is active").setContentText("Tap to open your desktop")
            .setContentIntent(home).addAction(new Notification.Action.Builder(null,"Stop dock",stop).build()).setOngoing(true).build());
        manager=getSystemService(WindowManager.class);
        displayContext=createDisplayContext(getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY));
        getSystemService(DisplayManager.class).registerDisplayListener(displayListener,new android.os.Handler(getMainLooper()));
        worker.execute(() -> { List<Apps.Entry> loaded=Apps.load(getApplicationContext());
            new android.os.Handler(getMainLooper()).post(() -> { if(destroyed) return; apps=loaded; appsReady=true; if(Settings.canDrawOverlays(this)) render(); else stopSelf(); }); });
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        int requested=intent==null?displayId:intent.getIntExtra("display_id",Display.DEFAULT_DISPLAY);
        Display display=getSystemService(DisplayManager.class).getDisplay(requested);
        if(display==null || !display.isValid()) { stopSelf(); return START_NOT_STICKY; }
        if(root!=null) { manager.removeView(root); root=null; }
        displayId=requested;
        displayContext=createDisplayContext(display);
        if(android.os.Build.VERSION.SDK_INT>=30)
            displayContext=displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        manager=displayContext.getSystemService(WindowManager.class);
        if(appsReady) render();
        return START_NOT_STICKY;
    }
    private void render() {
        if(root!=null) { manager.removeView(root); root=null; }
        if(!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        Context ui=displayContext;
        root=Ui.column(ui); root.setPadding(8,8,8,8); root.setBackground(Ui.bg(Ui.PANEL,Ui.dp(ui,16)));
        LinearLayout bar=new LinearLayout(ui); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(Ui.button(ui,compact?"◈":"Apps",() -> { compact=false; expanded=!expanded; render(); }));
        if(!compact) {
            HorizontalScrollView scroll=new HorizontalScrollView(ui); LinearLayout icons=new LinearLayout(ui);
            for(Apps.Entry e:Apps.pinned(this,apps)) {
                ImageView icon=new ImageView(ui); icon.setImageDrawable(e.icon); icon.setPadding(8,8,8,8);
                icon.setContentDescription(e.label); icon.setFocusable(true);
                icons.addView(icon,new LinearLayout.LayoutParams(Ui.dp(ui,44),Ui.dp(ui,44)));
                icon.setOnClickListener(v -> { expanded=false; render(); Apps.launch(ui,e); });
            }
            scroll.addView(icons); bar.addView(scroll,new LinearLayout.LayoutParams(0,-2,1));
            bar.addView(Ui.button(ui,"Home",() -> Displays.open(ui,displayId)));
            bar.addView(Ui.button(ui,"Hide",() -> { compact=true; expanded=false; render(); }));
        }
        if(expanded) {
            AppMenu menu=new AppMenu(ui,apps,() -> { expanded=false; render(); });
            root.addView(menu,new LinearLayout.LayoutParams(-1,Ui.dp(ui,Math.min(360,ui.getResources().getConfiguration().screenHeightDp-120))));
        }
        root.addView(bar);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(compact?-2:-1,-2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            expanded?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { manager.addView(root,params); }
        catch(RuntimeException ex) { root=null; stopSelf(); }
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); if(root!=null) render(); }
    @Override public void onDestroy() {
        destroyed=true; getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        super.onDestroy(); worker.shutdownNow(); if(root!=null) { manager.removeView(root); root=null; }
    }
}

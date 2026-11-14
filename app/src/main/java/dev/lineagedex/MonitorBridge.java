package dev.lineagedex;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.Toast;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Session-only companion credentials. No shell commands or arbitrary URLs from the UI. */
final class MonitorBridge {
    private static String token="";
    private static int port, display=-1;
    private static final ExecutorService queue=Executors.newSingleThreadExecutor();
    static void configure(Activity activity, Intent intent) {
        String value=intent.getStringExtra("bridge_token");
        int number=intent.getIntExtra("bridge_port",0);
        if(value!=null && value.matches("[0-9a-f]{64}") && number>0 && number<65536
                && Displays.currentId(activity)>0) {
            token=value; port=number; display=Displays.currentId(activity);
            Toast.makeText(activity,"PC window companion connected",Toast.LENGTH_SHORT).show();
        }
    }
    static boolean available(android.content.Context c) {
        return !token.isEmpty() && display==Displays.currentId(c);
    }
    static boolean launch(android.content.Context context,Apps.Entry entry,Rect bounds) {
        if(!available(context) || !(context instanceof Activity)) return false;
        Activity activity=(Activity)context;
        final String secret=token; final int endpoint=port, target=display;
        queue.execute(() -> {
            HttpURLConnection connection=null;
            try {
                JSONObject request=new JSONObject();
                request.put("component",entry.key()); request.put("display",target);
                request.put("bounds",new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
                connection=(HttpURLConnection)new URL("http://127.0.0.1:"+endpoint+"/launch").openConnection();
                connection.setConnectTimeout(2000); connection.setReadTimeout(120000);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Authorization","Bearer "+secret);
                connection.setRequestProperty("Content-Type","application/json");
                byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=connection.getOutputStream()) { out.write(bytes); }
                JSONObject response;
                try(InputStream in=connection.getInputStream()) {
                    ByteArrayOutputStream data=new ByteArrayOutputStream(); byte[] buffer=new byte[1024]; int count;
                    while((count=in.read(buffer))!=-1) {
                        if(data.size()+count>32768) throw new IOException("Companion response too large");
                        data.write(buffer,0,count);
                    }
                    response=new JSONObject(data.toString("UTF-8"));
                }
                if(!response.optBoolean("ok")) throw new IOException(response.optString("message","Window launch failed"));
            } catch(Exception error) {
                android.util.Log.e("LineageDeX","Companion window launch failed",error);
                activity.runOnUiThread(() -> {
                    if(!activity.isDestroyed()) new android.app.AlertDialog.Builder(activity)
                        .setTitle("Window placement not confirmed")
                        .setMessage(error.getMessage()+"\n\nKeep the PC companion running. Check its terminal for details.")
                        .setPositiveButton("OK",null).show();
                });
            } finally { if(connection!=null) connection.disconnect(); }
        });
        return true;
    }
}

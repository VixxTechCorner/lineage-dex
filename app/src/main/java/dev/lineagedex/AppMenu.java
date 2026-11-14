package dev.lineagedex;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import java.util.List;
import java.util.Locale;

final class AppMenu extends LinearLayout {
    private final GridLayout grid;
    private final List<Apps.Entry> apps;
    private final Runnable changed;
    AppMenu(Context c,List<Apps.Entry> entries,Runnable onChanged) {
        super(c); apps=entries; changed=onChanged; setOrientation(VERTICAL);
        EditText search=new EditText(c); search.setSingleLine(true); search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED); search.setHint("Search your apps");
        search.setTextSize(16); search.setPadding(Ui.dp(c,16),0,Ui.dp(c,16),0);
        search.setBackground(Ui.bg(android.graphics.Color.rgb(37,47,69),Ui.dp(c,14)));
        addView(search,new LayoutParams(-1,Ui.dp(c,52)));
        android.widget.TextView hint=Ui.text(c,"Hold an app for window options and desktop shortcuts",12,Ui.MUTED);
        hint.setPadding(0,Ui.dp(c,12),0,Ui.dp(c,12)); addView(hint);
        ScrollView scroll=new ScrollView(c); grid=new GridLayout(c);
        grid.setColumnCount(Math.max(2,Math.min(6,c.getResources().getConfiguration().screenWidthDp/110)));
        scroll.addView(grid); addView(scroll,new LayoutParams(-1,0,1)); render("");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { render(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
    }
    private void render(String filter) {
        grid.removeAllViews(); Context c=getContext(); int matches=0;
        for (Apps.Entry e:apps) {
            if (!e.label.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
            matches++;
            LinearLayout tile=Ui.column(c); tile.setGravity(Gravity.CENTER);
            tile.setPadding(4,Ui.dp(c,10),4,Ui.dp(c,10)); tile.setFocusable(true);
            tile.setBackground(Ui.selectable(c));
            ImageView icon=new ImageView(c); icon.setImageDrawable(e.icon);
            tile.addView(icon,new LayoutParams(Ui.dp(c,40),Ui.dp(c,40)));
            TextView label=Ui.text(c,e.label,12,Ui.TEXT); label.setGravity(Gravity.CENTER);
            label.setMaxLines(2); tile.addView(label,new LayoutParams(-1,Ui.dp(c,38)));
            tile.setContentDescription(e.label);
            tile.setOnClickListener(v -> { changed.run(); Apps.launch(c,e); });
            tile.setOnLongClickListener(v -> { Apps.showActions(c,e,apps,changed); return true; });
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();
            p.width=0; p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); grid.addView(tile,p);
        }
        if (matches==0) grid.addView(Ui.text(c,"No apps found",16,Ui.MUTED));
    }
}

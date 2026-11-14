package dev.lineagedex;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(16,26,41), PANEL = Color.rgb(27,43,61);
    static final int TEXT = Color.rgb(237,244,249), MUTED = Color.rgb(157,180,197);
    static final int MINT = Color.rgb(148,246,209);
    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    static Drawable selectable(Context c) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(45,218,229,255)),
            null,bg(Color.WHITE,dp(c,14)));
    }
    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    static TextView text(Context c, String s, int size, int color) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(color); return v;
    }
    static void reveal(android.view.View view,long delay) {
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        view.setAlpha(0f); view.setTranslationY(dp(view.getContext(),12));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(220)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    static void pressFeedback(android.view.View view) {
        view.setOnTouchListener((v,event) -> {
            if(android.animation.ValueAnimator.areAnimatorsEnabled()) {
                int action=event.getActionMasked();
                if(action==android.view.MotionEvent.ACTION_DOWN)
                    v.animate().scaleX(.96f).scaleY(.96f).setStartDelay(0).setDuration(90).start();
                else if(action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(130).start();
            }
            return false; // Keep Android's normal click, scrolling and long-press handling.
        });
    }
    static TextView button(Context c, String s, Runnable action) {
        TextView v = text(c,s,14,TEXT); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(45,218,229,255)),
            bg(PANEL,dp(c,12)),bg(Color.WHITE,dp(c,12))));
        v.setTypeface(null,Typeface.BOLD); pressFeedback(v);
        v.setOnClickListener(w -> action.run()); v.setFocusable(true); return v;
    }
}

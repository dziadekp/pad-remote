package com.padremote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Map;

/**
 * Home-screen widget. Two layouts: compact (2 rows) and tall (3+ rows, big centred speed plus
 * stat tiles). On Android 12+ the launcher picks the one that fits the widget's current size.
 */
public class PadWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) { refresh(ctx); }

    @Override public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) { refresh(ctx); }

    static PendingIntent action(Context ctx, String action) {
        Intent i = new Intent(ctx, PadService.class).setAction(action);
        return PendingIntent.getForegroundService(ctx, action.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName me = new ComponentName(ctx, PadWidget.class);
        if (mgr.getAppWidgetIds(me).length == 0) return;
        RemoteViews compact = fill(ctx, new RemoteViews(ctx.getPackageName(), R.layout.widget), false);
        RemoteViews tall = fill(ctx, new RemoteViews(ctx.getPackageName(), R.layout.widget_tall), true);
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= 31) {
            Map<SizeF, RemoteViews> bySize = new HashMap<>();
            bySize.put(new SizeF(200f, 90f), compact);
            bySize.put(new SizeF(200f, 210f), tall);
            views = new RemoteViews(bySize);
        } else {
            views = compact;
        }
        mgr.updateAppWidget(me, views);
    }

    private static RemoteViews fill(Context ctx, RemoteViews v, boolean tall) {
        PadProtocol.Status s = PadService.status;
        boolean on = PadService.connected;
        boolean moving = s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING;

        v.setTextViewText(R.id.w_speed, Fmt.mph(on ? PadProtocol.rawToMph(s.speedRaw) : 0));
        v.setTextColor(R.id.w_speed, ctx.getColor(moving ? R.color.go : R.color.text));
        v.setTextViewText(R.id.w_dist, Fmt.miles(s.distM / PadProtocol.M_PER_MILE));
        Estimates.Shown e = Profile.shown(ctx, s);
        v.setTextViewText(R.id.w_steps, Profile.steps(e));
        v.setTextViewText(R.id.w_cal, Profile.calories(e));
        String state = on ? PadService.stateWord(s.state) : shortLink();
        if (tall) {
            v.setTextViewText(R.id.w_time, Fmt.time(s.durSec));
            v.setTextViewText(R.id.w_status, state);
        } else {   // compact has no time tile; time rides along in the status line
            v.setTextViewText(R.id.w_status, on && s.durSec > 0 ? state + " · " + Fmt.time(s.durSec) : state);
        }
        v.setTextColor(R.id.w_dot, ctx.getColor(!on ? R.color.muted : moving ? R.color.go : R.color.accent));
        if (tall) {
            v.setTextViewText(R.id.w_target, (moving ? "Target " : "Starts at ") + Fmt.mph(PadService.targetMph) + " mph");
        }
        v.setTextViewText(R.id.w_start, moving ? "Running" : "Start");
        v.setInt(R.id.w_start, "setBackgroundResource", moving ? R.drawable.bg_tile : R.drawable.bg_go);

        v.setOnClickPendingIntent(R.id.w_down, action(ctx, PadService.ACTION_DOWN));
        v.setOnClickPendingIntent(R.id.w_up, action(ctx, PadService.ACTION_UP));
        v.setOnClickPendingIntent(R.id.w_start, action(ctx, PadService.ACTION_START));
        v.setOnClickPendingIntent(R.id.w_stop, action(ctx, PadService.ACTION_STOP));
        v.setOnClickPendingIntent(R.id.w_info, PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    /** The pill is small; keep the not-connected text short. */
    private static String shortLink() {
        String l = PadService.link;
        if (l == null || l.startsWith("Not connected")) return "Not connected";
        return l.length() > 22 ? l.substring(0, 21) + "…" : l;
    }
}

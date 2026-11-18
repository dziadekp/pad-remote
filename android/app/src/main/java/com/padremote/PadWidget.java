package com.padremote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: live speed / distance / time plus − Start STOP + buttons. */
public class PadWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) { refresh(ctx); }

    static PendingIntent action(Context ctx, String action) {
        Intent i = new Intent(ctx, PadService.class).setAction(action);
        return PendingIntent.getForegroundService(ctx, action.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        ComponentName me = new ComponentName(ctx, PadWidget.class);
        if (mgr.getAppWidgetIds(me).length == 0) return;
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        PadProtocol.Status s = PadService.status;
        boolean on = PadService.connected;
        boolean moving = s.state == PadProtocol.State.RUNNING || s.state == PadProtocol.State.STARTING;
        v.setTextViewText(R.id.w_speed, on ? String.format("%.1f", PadProtocol.rawToMph(s.speedRaw)) : "–");
        v.setTextViewText(R.id.w_dist, on ? String.format("%.2f mi", s.distM / PadProtocol.M_PER_MILE) : "– mi");
        v.setTextViewText(R.id.w_time, on ? MainActivity.fmtTime(s.durSec) : "–:––");
        v.setTextViewText(R.id.w_status, on
                ? PadService.stateWord(s.state) + " · " + (moving ? "target " : "start at ") + String.format("%.1f mph", PadService.targetMph)
                : PadService.link + " · tap Start");
        v.setOnClickPendingIntent(R.id.w_down, action(ctx, PadService.ACTION_DOWN));
        v.setOnClickPendingIntent(R.id.w_up, action(ctx, PadService.ACTION_UP));
        v.setOnClickPendingIntent(R.id.w_start, action(ctx, PadService.ACTION_START));
        v.setOnClickPendingIntent(R.id.w_stop, action(ctx, PadService.ACTION_STOP));
        v.setOnClickPendingIntent(R.id.w_info, PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        mgr.updateAppWidget(me, v);
    }
}

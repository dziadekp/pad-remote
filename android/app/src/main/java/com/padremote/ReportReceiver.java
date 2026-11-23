package com.padremote;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Evening walk report for the doctor. At 9 pm a notification shows the day's totals; tapping it opens
 * the Messages app with the text written and the doctor's number filled in. Peter presses Send himself:
 * this app never sends a text on its own.
 */
public class ReportReceiver extends BroadcastReceiver {
    static final String ACTION_REPORT = "com.padremote.REPORT";
    static final int HOUR = 21;
    private static final String CHANNEL = "doctor_report";
    private static final int NOTE_ID = 21;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("pad", Context.MODE_PRIVATE); }
    static String number(Context c) { return prefs(c).getString("doctorNumber", ""); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("doctorReport", false) && !number(c).isEmpty(); }

    @Override public void onReceive(Context c, Intent i) {
        if (ACTION_REPORT.equals(i.getAction()) && enabled(c)) show(c);
        schedule(c);   // also covers phone restart and app update
    }

    /** Sets (or clears) the next 9 pm alarm. Within a 10-minute window, so no special alarm permission. */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0,
                new Intent(c, ReportReceiver.class).setAction(ACTION_REPORT), PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (enabled(c)) am.setWindow(AlarmManager.RTC_WAKEUP,
                DailyReport.nextAt(System.currentTimeMillis(), HOUR), 10 * 60 * 1000, pi);
    }

    /** Posts the notification now (used at 9 pm and by "Preview now"). */
    static void show(Context c) {
        String text = DailyReport.text(SessionStore.get(c).all(), DailyReport.dayStart(System.currentTimeMillis()));
        Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number(c))))
                .putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(c, 1, sms,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Doctor report", NotificationManager.IMPORTANCE_HIGH));
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_pad)
                .setContentTitle("Send today's walk to your doctor?")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\n\nTap to open Messages, then press Send."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTE_ID, n);
    }
}

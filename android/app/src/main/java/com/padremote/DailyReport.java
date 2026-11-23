package com.padremote;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The evening text to the doctor: the day's walking totals. Plain Java so it can be unit tested. */
public final class DailyReport {
    private DailyReport() {}

    /** Message for the day that starts at dayStartMs (local midnight). */
    public static String text(List<SessionTracker.Session> sessions, long dayStartMs) {
        long dayEndMs = dayStartMs + 24L * 3600 * 1000;
        long steps = 0, sec = 0; int n = 0;
        for (SessionTracker.Session s : sessions) {
            if (s.startMs < dayStartMs || s.startMs >= dayEndMs) continue;
            steps += s.steps; sec += s.durSec; n++;
        }
        String day = new SimpleDateFormat("EEE, MMM d", Locale.US).format(new Date(dayStartMs));
        if (n == 0) return "Treadmill " + day + ": no walk today.";
        return "Treadmill " + day + ": " + Fmt.count(steps) + " steps, " + minutes(sec) + " on the treadmill.";
    }

    /** 58 min, or 1 h 05 min. */
    static String minutes(long sec) {
        long m = Math.round(sec / 60.0);
        return m < 60 ? m + " min" : String.format(Locale.US, "%d h %02d min", m / 60, m % 60);
    }

    /** Local midnight at the start of the day containing ms. */
    public static long dayStart(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Next time (after nowMs) the clock shows hour:00. */
    public static long nextAt(long nowMs, int hour) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= nowMs) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }
}

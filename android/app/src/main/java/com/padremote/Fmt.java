package com.padremote;

import java.util.Locale;

/** Number formatting shared by the app, widget and history. */
final class Fmt {
    private Fmt() {}
    static String time(long sec) {
        long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
    static String mph(double v) { return String.format(Locale.US, "%.1f", v); }
    static String miles(double v) { return String.format(Locale.US, "%.2f", v); }
    static String count(long n) { return String.format(Locale.US, "%,d", n); }
    /** Minutes per mile, e.g. 40:00. */
    static String pace(double mph) {
        if (mph < 0.1) return "–";
        long sec = Math.round(3600 / mph);
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }
}

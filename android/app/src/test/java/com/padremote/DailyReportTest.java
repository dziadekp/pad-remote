package com.padremote;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.junit.Test;

public class DailyReportTest {
    static long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance();
        c.clear(); c.set(y, mo - 1, d, h, mi);
        return c.getTimeInMillis();
    }
    static SessionTracker.Session walk(long start, long sec, long m, long steps) {
        SessionTracker.Session s = new SessionTracker.Session();
        s.startMs = start; s.durSec = sec; s.distM = m; s.steps = steps;
        return s;
    }

    @Test public void sumsTodaysWalksOnly() {
        List<SessionTracker.Session> all = new ArrayList<>();
        all.add(walk(at(2026, 11, 23, 10, 35), 1800, 2414, 3450));
        all.add(walk(at(2026, 11, 23, 18, 0), 1680, 2253, 3200));
        all.add(walk(at(2026, 11, 22, 19, 0), 3600, 4828, 7000));   // yesterday: left out
        assertEquals("Treadmill Mon, Nov 23: 6,650 steps, 58 min on the treadmill.",
                DailyReport.text(all, DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void overAnHourShowsHours() {
        List<SessionTracker.Session> all = new ArrayList<>();
        all.add(walk(at(2026, 11, 23, 10, 35), 3900, 5200, 7500));
        assertEquals("Treadmill Mon, Nov 23: 7,500 steps, 1 h 05 min on the treadmill.",
                DailyReport.text(all, DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void sayssoWhenNoWalk() {
        assertEquals("Mon, Nov 23: no treadmill walk today.",
                DailyReport.text(new ArrayList<>(), DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void nextNinePm() {
        assertEquals(at(2026, 11, 23, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 10, 40), 21));
        assertEquals(at(2026, 11, 24, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 21, 0), 21));
        assertEquals(at(2026, 11, 24, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 22, 15), 21));
    }
}

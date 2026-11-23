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
        assertEquals("Walk report for Mon, Nov 23: 6,650 steps, 2.9 mi, 58 min (2 walks).",
                DailyReport.text(all, DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void oneWalkHasNoCount() {
        List<SessionTracker.Session> all = new ArrayList<>();
        all.add(walk(at(2026, 11, 23, 10, 35), 1800, 2414, 3450));
        assertEquals("Walk report for Mon, Nov 23: 3,450 steps, 1.5 mi, 30 min.",
                DailyReport.text(all, DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void sayssoWhenNoWalk() {
        assertEquals("Walk report for Mon, Nov 23: no walk today.",
                DailyReport.text(new ArrayList<>(), DailyReport.dayStart(at(2026, 11, 23, 21, 0))));
    }
    @Test public void nextNinePm() {
        assertEquals(at(2026, 11, 23, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 10, 40), 21));
        assertEquals(at(2026, 11, 24, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 21, 0), 21));
        assertEquals(at(2026, 11, 24, 21, 0), DailyReport.nextAt(at(2026, 11, 23, 22, 15), 21));
    }
}

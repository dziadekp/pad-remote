package com.padremote;

import static org.junit.Assert.*;

import org.junit.Test;

public class SessionTrackerTest {
    static PadProtocol.Status st(PadProtocol.State state, int speed, long dist, long dur, long steps, int cal) {
        PadProtocol.Status s = new PadProtocol.Status();
        s.state = state; s.speedRaw = speed; s.distM = dist; s.durSec = dur; s.steps = steps; s.calories = cal;
        return s;
    }

    @Test public void walkThenStopSavesOneSession() {
        SessionTracker t = new SessionTracker();
        long t0 = 1_000_000;
        assertNull(t.onStatus(st(PadProtocol.State.STOPPED, 0, 0, 0, 0, 0), t0));
        for (int i = 1; i <= 120; i++) t.onStatus(st(PadProtocol.State.RUNNING, 2400, i * 2L / 3, i, i * 2L, i / 20), t0 + i * 1000L);
        SessionTracker.Session s = t.onStatus(st(PadProtocol.State.STOPPED, 0, 80, 120, 240, 6), t0 + 121_000);
        assertNotNull(s);
        assertEquals("stopped", s.endedBy);
        assertEquals(120, s.durSec);
        assertEquals(80, s.distM);
        assertEquals(240, s.steps);
        assertEquals(6, s.calories);
        assertEquals(t0, s.startMs);                     // start = first running frame minus pad time
        assertEquals(1.49, s.maxMph, 0.01);              // 2400 m/h
        assertNull(t.current());
    }

    @Test public void falseStartIsNotSaved() {
        SessionTracker t = new SessionTracker();
        t.onStatus(st(PadProtocol.State.RUNNING, 1000, 1, 3, 0, 0), 5000);
        assertNull(t.onStatus(st(PadProtocol.State.STOPPED, 0, 1, 3, 0, 0), 6000));
    }

    @Test public void joiningAWalkUnderWayBackdatesStart() {
        SessionTracker t = new SessionTracker();
        t.onStatus(st(PadProtocol.State.RUNNING, 2400, 500, 600, 900, 30), 10_000_000);
        assertEquals(10_000_000 - 600_000, t.current().startMs);
    }

    @Test public void linkLostClosesSession() {
        SessionTracker t = new SessionTracker();
        for (int i = 1; i <= 40; i++) t.onStatus(st(PadProtocol.State.RUNNING, 2400, i, i, i, 0), i * 1000L);
        SessionTracker.Session s = t.onLinkLost(50_000);
        assertNotNull(s);
        assertEquals("connection lost", s.endedBy);
    }

    @Test public void savesPeriodicallyWhileRunning() {
        SessionTracker t = new SessionTracker();
        int saves = 0;
        for (int i = 1; i <= 60; i++)
            if (t.onStatus(st(PadProtocol.State.RUNNING, 2400, i * 10L, i, i, 0), i * 1000L) != null) saves++;
        assertEquals(4, saves);
    }
}

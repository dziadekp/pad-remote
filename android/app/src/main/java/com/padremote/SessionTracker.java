package com.padremote;

/**
 * Turns the pad's once-a-second status into walking sessions. Plain Java so it can be unit tested.
 * A session opens when the belt runs and closes when the pad reports STOPPED or the link drops.
 * The pad's own counters (distance, steps, calories, time) reset at each start, so a session simply
 * keeps the latest values it saw.
 */
public final class SessionTracker {

    public static final class Session {
        public long id = -1;
        public long startMs, endMs;
        public long durSec, distM, steps;
        public int calories;
        public double maxMph;
        public String endedBy = "";
        /** Set on sessions read back from history: bit 0 = steps estimated, bit 1 = calories estimated. */
        public int estFlags;

        public double avgMph() { return durSec > 0 ? (distM / PadProtocol.M_PER_MILE) / (durSec / 3600.0) : 0; }
        public double miles() { return distM / PadProtocol.M_PER_MILE; }
        /** Too short to be worth keeping (a test tap, a false start). */
        public boolean trivial() { return durSec < 20 && distM < 10; }
    }

    private Session cur;
    private int sinceSave;

    public Session current() { return cur; }

    /**
     * Feed one status frame. Returns the session to save now (still open or just closed), or null
     * when nothing needs saving. Open sessions are saved about every 15 frames so a crash loses little.
     */
    public Session onStatus(PadProtocol.Status s, long nowMs) {
        boolean running = s.state == PadProtocol.State.RUNNING;
        if (running) {
            if (cur == null) {
                cur = new Session();
                cur.startMs = nowMs - s.durSec * 1000;  // joins a walk already under way at its real start
                sinceSave = 0;
            }
            absorb(s, nowMs);
            if (++sinceSave >= 15) { sinceSave = 0; return cur.trivial() ? null : cur; }
            return null;
        }
        if (cur != null && s.state == PadProtocol.State.STOPPED) {
            absorb(s, nowMs);
            return close("stopped", nowMs);
        }
        if (cur != null) absorb(s, nowMs);   // paused / starting: keep the session open
        return null;
    }

    /** Link lost or the app let go. Returns the session to save, or null. */
    public Session onLinkLost(long nowMs) {
        return cur == null ? null : close("connection lost", nowMs);
    }

    private Session close(String why, long nowMs) {
        Session done = cur;
        cur = null;
        done.endedBy = why;
        done.endMs = nowMs;
        return done.trivial() && done.id < 0 ? null : done;
    }

    private void absorb(PadProtocol.Status s, long nowMs) {
        // counters only grow during a walk; ignore a stray zeroed frame
        cur.durSec = Math.max(cur.durSec, s.durSec);
        cur.distM = Math.max(cur.distM, s.distM);
        cur.steps = Math.max(cur.steps, s.steps);
        cur.calories = Math.max(cur.calories, s.calories);
        if (s.state == PadProtocol.State.RUNNING) cur.maxMph = Math.max(cur.maxMph, PadProtocol.rawToMph(s.speedRaw));
        cur.endMs = nowMs;
    }
}

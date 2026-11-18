package com.padremote;

import java.util.UUID;

/**
 * PitPat-T01 walking pad protocol (verified on Peter's pad 2026-11-18).
 * Service FFFF; write FF01 (write WITH response only); notify FF02.
 * The pad sends a status frame about once a second and expects one reply per frame
 * (a heartbeat or a queued command), otherwise it drops the link after about 10 s.
 * Speed unit is metres per hour (km/h x 1000), 1000..maxSpeed. Distance in metres.
 */
public final class PadProtocol {
    private PadProtocol() {}

    public static final UUID SERVICE = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    public static final UUID NOTIFY = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final double M_PER_MILE = 1609.344;
    public static final int MIN_RAW = 1000;
    public static final int DEFAULT_MAX_RAW = 6000;
    private static final long USER_ID = 58965456623L;
    private static final byte[] HEARTBEAT = {0x05, 0x6a, 0x05, (byte) 0xfd, (byte) 0xf8, 0x43};

    public enum State { STOPPED, STARTING, RUNNING, PAUSED }

    public static final class Status {
        public State state = State.STOPPED;
        public int speedRaw, targetRaw, maxRaw = DEFAULT_MAX_RAW;
        public long distM, steps, durSec;
        public int calories, heartRate;
    }

    static byte[] command(int cmd, int speedRaw, int ramp, int weightKg) {
        byte[] p = new byte[23];
        p[0] = 0x6a; p[1] = 0x17;
        p[6] = (byte) (speedRaw >> 8); p[7] = (byte) speedRaw;
        p[8] = (byte) ramp; p[9] = 0; p[10] = (byte) weightKg; p[11] = 0;
        p[12] = (byte) (cmd | 0x08); // bit 3 = miles mode, matches the pad's setting
        for (int i = 0; i < 8; i++) p[13 + i] = (byte) (USER_ID >> (56 - i * 8));
        int c = 0;
        for (int i = 1; i <= 20; i++) c ^= p[i] & 0xff;
        p[21] = (byte) c; p[22] = 0x43;
        return p;
    }

    public static final int DEFAULT_WEIGHT_KG = 80;
    /** Weight is used by the pad for its calorie estimate. */
    public static byte[] start(int speedRaw, int weightKg) { return command(4, speedRaw, 1, weightKg); }
    public static byte[] setSpeed(int speedRaw, int weightKg) { return command(4, speedRaw, 5, weightKg); }
    public static byte[] stop(int weightKg) { return command(0, 0, 1, weightKg); }
    public static byte[] start(int speedRaw) { return start(speedRaw, DEFAULT_WEIGHT_KG); }
    public static byte[] setSpeed(int speedRaw) { return setSpeed(speedRaw, DEFAULT_WEIGHT_KG); }
    public static byte[] stop() { return stop(DEFAULT_WEIGHT_KG); }

    /** Wraps a reply in the outer 4D frame. body == null means heartbeat. */
    public static byte[] frame(int seq, byte[] body) {
        if (body == null) {
            byte[] out = new byte[3 + HEARTBEAT.length];
            out[0] = 0x4d; out[1] = 0; out[2] = (byte) seq;
            System.arraycopy(HEARTBEAT, 0, out, 3, HEARTBEAT.length);
            return out;
        }
        byte[] out = new byte[4 + body.length];
        out[0] = 0x4d; out[1] = 0; out[2] = (byte) seq; out[3] = (byte) body.length;
        System.arraycopy(body, 0, out, 4, body.length);
        return out;
    }

    /** Returns null if the frame is not a status frame. */
    public static Status decode(byte[] d) {
        if (d == null || d.length < 35 || (d[4] & 0xff) != 0x68) return null;
        Status s = new Status();
        s.speedRaw = u16(d, 4 + 3);
        s.targetRaw = u16(d, 4 + 5);
        s.distM = u32(d, 4 + 7);
        s.steps = u32(d, 4 + 14);
        s.heartRate = d[4 + 13] & 0xff;
        s.calories = u16(d, 4 + 18);
        long dur = u32(d, 4 + 20);
        int fw = d[4 + 25] & 0xff;
        s.durSec = fw > 19 ? dur / 1000 : dur;
        int flags = d[4 + 26] & 0xff;
        switch (flags & 24) {
            case 24: s.state = State.STARTING; break;
            case 8: s.state = State.RUNNING; break;
            case 16: s.state = State.PAUSED; break;
            default: s.state = State.STOPPED;
        }
        int max = u16(d, 4 + 27);
        if (max >= MIN_RAW && max <= 20000) s.maxRaw = max;
        return s;
    }

    /**
     * Android's default Bluetooth packet size cuts the pad's 56-byte status frame into 20-byte
     * pieces. This glues pieces back together. Outer frame: 4D xx seq len, then len bytes.
     */
    public static final class Assembler {
        private final byte[] buf = new byte[512];
        private int n = 0;

        /** Returns a complete frame, or null while still waiting for more pieces. */
        public byte[] push(byte[] chunk) {
            if (chunk.length > 0 && (chunk[0] & 0xff) == 0x4d) n = 0;   // a new frame starts here
            else if (n == 0) return null;                              // stray tail; skip it
            if (n + chunk.length > buf.length) { n = 0; return null; }
            System.arraycopy(chunk, 0, buf, n, chunk.length);
            n += chunk.length;
            if (n < 4) return null;
            int total = 4 + (buf[3] & 0xff);
            if (n < total) return null;
            byte[] out = java.util.Arrays.copyOf(buf, total);
            n = 0;
            return out;
        }
    }

    public static int mphToRaw(double mph, int maxRaw) {
        return (int) Math.max(MIN_RAW, Math.min(maxRaw, Math.round(mph * M_PER_MILE)));
    }

    public static double rawToMph(int raw) { return raw / M_PER_MILE; }

    private static int u16(byte[] d, int i) { return ((d[i] & 0xff) << 8) | (d[i + 1] & 0xff); }

    private static long u32(byte[] d, int i) {
        return ((long) (d[i] & 0xff) << 24) | ((d[i + 1] & 0xff) << 16) | ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
    }
}

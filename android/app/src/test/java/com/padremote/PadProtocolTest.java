package com.padremote;

import static org.junit.Assert.*;

import org.junit.Test;

/** Expected bytes are the exact frames that moved Peter's pad on 2026-11-18. */
public class PadProtocolTest {
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    @Test public void startMatchesPad() {
        assertEquals("4d0003176a170000000003e8010050000c0000000dba9d76ef1243",
                hex(PadProtocol.frame(3, PadProtocol.start(1000))));
    }
    @Test public void setSpeedMatchesPad() {
        assertEquals("4d0016176a170000000007d0050050000c0000000dba9d76ef2a43",
                hex(PadProtocol.frame(0x16, PadProtocol.setSpeed(2000))));
    }
    @Test public void stopMatchesPad() {
        assertEquals("4d0032176a1700000000000001005000080000000dba9d76effd43",
                hex(PadProtocol.frame(0x32, PadProtocol.stop())));
    }
    @Test public void heartbeat() {
        assertEquals("4d0007056a05fdf843", hex(PadProtocol.frame(7, null)));
    }
    @Test public void decodesStoppedStatus() {
        PadProtocol.Status s = PadProtocol.decode(unhex(
                "4d040134683400000000000000015200000000000000000000000000351b82177000050074434d6c3133455658324663363333300e02eb43"));
        assertNotNull(s);
        assertEquals(PadProtocol.State.STOPPED, s.state);
        assertEquals(338, s.distM);
        assertEquals(6000, s.maxRaw);
    }
    @Test public void speedConversionMatchesDisplay() {
        assertEquals(1000, PadProtocol.mphToRaw(0.6, 6000));
        assertEquals(6000, PadProtocol.mphToRaw(9.0, 6000));
        assertEquals(1.3, Math.round(PadProtocol.rawToMph(2100) * 10) / 10.0, 1e-9);
    }
}

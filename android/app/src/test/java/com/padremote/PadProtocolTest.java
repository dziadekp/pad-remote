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
    @Test public void readsStepsFromMotorFieldWhenMainFieldIsZero() {
        // captured on the pad 2026-11-23, 3.0 mph, 140 s into a walk
        PadProtocol.Status s = PadProtocol.decode(unhex(
                "4d04192f682f0012c012c0000000ba000000000000000012000222e03e1b8a177000050000000000000000003808c700fa8743"));
        assertEquals(PadProtocol.State.RUNNING, s.state);
        assertEquals(186, s.distM);
        assertEquals(250, s.steps);
    }
    @Test public void firstFrameSerialIsNotReadAsSteps() {
        // 52-byte first frame: payload 43-44 hold serial number text, not steps
        PadProtocol.Status s = PadProtocol.decode(unhex(
                "4d0401346834000000000000000185000000000000000000000000003d1b82177000050074434d6c3133455658324663363333300e023443"));
        assertEquals(0, s.steps);
    }
    @Test public void assemblesFrameSplitInto20BytePieces() {
        String hex = "4d040134683400000000000000015200000000000000000000000000351b82177000050074434d6c3133455658324663363333300e02eb43";
        byte[] full = unhex(hex);
        PadProtocol.Assembler a = new PadProtocol.Assembler();
        assertNull(a.push(java.util.Arrays.copyOfRange(full, 0, 20)));
        assertNull(a.push(java.util.Arrays.copyOfRange(full, 20, 40)));
        byte[] out = a.push(java.util.Arrays.copyOfRange(full, 40, full.length));
        assertEquals(hex, hex(out));
        assertEquals(338, PadProtocol.decode(out).distM);
        // a whole frame in one piece (big packets) also passes straight through
        assertEquals(hex, hex(a.push(full)));
    }

    @Test public void speedConversionMatchesDisplay() {
        assertEquals(1000, PadProtocol.mphToRaw(0.6, 6000));
        assertEquals(6000, PadProtocol.mphToRaw(9.0, 6000));
        assertEquals(1.3, Math.round(PadProtocol.rawToMph(2100) * 10) / 10.0, 1e-9);
    }
}

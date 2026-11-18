package com.padremote;

import static org.junit.Assert.*;

import org.junit.Test;

public class EstimatesTest {
    @Test public void caloriesFollowAcsmWalking() {
        // 80 kg, 1.5 mph (40.2 m/min) for 60 min = 2414 m: VO2 = 7.52 ml/kg/min -> 3.01 kcal/min -> ~181 kcal
        assertEquals(181, Estimates.calories(2414, 3600, 80), 1);
    }
    @Test public void stepsFromHeight() {
        // 178 cm -> stride 0.739 m; 1 mile = 1609 m -> ~2178 steps
        assertEquals(2178, Estimates.steps(1609, 178), 2);
    }
    @Test public void prefersPadCountsWhenPresent() {
        Estimates.Shown e = Estimates.pick(1234, 56, 1000, 600, 80, 178);
        assertEquals(1234, e.steps); assertFalse(e.stepsEstimated);
        assertEquals(56, e.calories); assertFalse(e.caloriesEstimated);
    }
    @Test public void estimatesWhenPadSendsZero() {
        Estimates.Shown e = Estimates.pick(0, 0, 1000, 600, 80, 178);
        assertTrue(e.stepsEstimated); assertTrue(e.caloriesEstimated);
        assertTrue(e.steps > 0); assertTrue(e.calories > 0);
    }
}

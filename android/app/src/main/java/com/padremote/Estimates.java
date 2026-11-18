package com.padremote;

/**
 * Fallbacks for when the pad doesn't report steps or calories (it sends 0).
 * Calories: ACSM walking equation, level ground: VO2 (ml/kg/min) = 0.1 x speed(m/min) + 3.5,
 *   kcal = VO2 x kg / 1000 x 5. Over a whole walk: kcal = 5 x kg / 1000 x (0.1 x metres + 3.5 x minutes).
 * Steps: distance / stride, stride ~ 0.415 x height (common walking rule of thumb).
 */
public final class Estimates {
    private Estimates() {}

    public static int calories(long distM, long durSec, int weightKg) {
        if (durSec <= 0 || weightKg <= 0) return 0;
        double litresO2 = (0.1 * distM + 3.5 * durSec / 60.0) * weightKg / 1000.0;
        return (int) Math.round(litresO2 * 5.0);
    }

    public static long steps(long distM, int heightCm) {
        if (distM <= 0 || heightCm <= 0) return 0;
        double strideM = 0.415 * heightCm / 100.0;
        return Math.round(distM / strideM);
    }

    /** What to show: the pad's count if it has one, else the estimate. */
    public static final class Shown {
        public final long steps; public final int calories;
        public final boolean stepsEstimated, caloriesEstimated;
        Shown(long st, boolean se, int cal, boolean ce) { steps = st; stepsEstimated = se; calories = cal; caloriesEstimated = ce; }
    }

    public static Shown pick(long padSteps, int padCal, long distM, long durSec, int weightKg, int heightCm) {
        boolean se = padSteps <= 0, ce = padCal <= 0;
        return new Shown(se ? steps(distM, heightCm) : padSteps, se,
                ce ? calories(distM, durSec, weightKg) : padCal, ce);
    }
}

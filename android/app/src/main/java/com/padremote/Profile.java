package com.padremote;

import android.content.Context;

/** Weight and height, used for the pad's calorie estimate and the app's fallbacks. */
final class Profile {
    private Profile() {}
    static final int DEFAULT_HEIGHT_CM = 178;   // 5'10"
    static int weightKg(Context c) { return c.getSharedPreferences("pad", Context.MODE_PRIVATE).getInt("weightKg", PadProtocol.DEFAULT_WEIGHT_KG); }
    static int heightCm(Context c) { return c.getSharedPreferences("pad", Context.MODE_PRIVATE).getInt("heightCm", DEFAULT_HEIGHT_CM); }
    static Estimates.Shown shown(Context c, PadProtocol.Status s) {
        return Estimates.pick(s.steps, s.calories, s.distM, s.durSec, weightKg(c), heightCm(c));
    }
    static String steps(Estimates.Shown e) { return (e.stepsEstimated && e.steps > 0 ? "~" : "") + Fmt.count(e.steps); }
    static String calories(Estimates.Shown e) { return (e.caloriesEstimated && e.calories > 0 ? "~" : "") + Fmt.count(e.calories); }
}

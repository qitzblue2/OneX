package dev.onex.npccoach.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rule-based report used when no key is set or every key failed. */
public final class OfflineCoach {
    private OfflineCoach() {}

    public static CoachReport analyze(FightSummary f, String reason) {
        List<String> good = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        Drill drill = Drill.NORMAL;
        double worst = 0;

        double acc = f.accuracy();
        if (f.swings() >= 5 && acc >= 0.6) good.add(pct("Accurate swings", acc));
        if (f.swings() >= 5 && acc < 0.45) {
            bad.add(pct("Low accuracy", acc));
            tips.add("Wait for the full attack cooldown and keep your crosshair on the coach's chest.");
            if (0.45 - acc > worst) { worst = 0.45 - acc; drill = Drill.STRAFE; }
        }
        if (f.hits() > 0 && f.crits() * 4 < f.hits()) {
            bad.add("Few critical hits (" + f.crits() + ")");
            tips.add("Jump and hit while falling for 50% extra damage.");
            if (0.2 > worst) { worst = 0.2; drill = Drill.COMBO; }
        } else if (f.crits() > 0) good.add(f.crits() + " critical hits");
        if (f.hitsTaken() > f.hits() + 3) {
            bad.add("Took more hits (" + f.hitsTaken() + ") than you landed (" + f.hits() + ")");
            tips.add("Back off after landing a hit and strafe instead of walking straight in.");
            double gap = (f.hitsTaken() - f.hits()) / 10.0;
            if (gap > worst) { worst = gap; drill = Drill.RUSH; }
        }
        if (f.strafePercent() < 20) {
            bad.add(String.format(Locale.ROOT, "Barely strafing (%.0f%%)", f.strafePercent()));
            tips.add("Hold A or D while fighting so you're harder to hit.");
        } else good.add("Good movement");
        if (f.hitsBlockedByCoach() >= 3) {
            bad.add(f.hitsBlockedByCoach() + " hits wasted on the shield");
            tips.add("Use an axe to disable shields, or wait for the shield to drop.");
            if (0.3 > worst) { worst = 0.3; drill = Drill.SHIELD; }
        }
        if (f.avgDistance() > 4.0) {
            bad.add(String.format(Locale.ROOT, "Stayed too far away (%.1f blocks)", f.avgDistance()));
            tips.add("Sprint in to close distance, then hit right as you enter reach.");
            if (0.25 > worst) drill = Drill.KITE;
        }
        if (f.longestCombo() >= 4) good.add("Combo of " + f.longestCombo() + " hits");
        if (good.isEmpty()) good.add("You showed up and fought, that's step one");
        if (tips.isEmpty()) tips.add("Try a harder drill to keep improving.");

        String summary = switch (f.result()) {
            case "WIN" -> "Nice win! Here's what to sharpen next.";
            case "LOSS" -> "I got you this time, but here's how to turn it around.";
            default -> "Time ran out. Let's look at the numbers.";
        };
        return new CoachReport(summary, good, bad.isEmpty() ? List.of("Nothing major, keep practicing") : bad, tips, drill, "offline coach: " + reason);
    }

    private static String pct(String label, double v) {
        return String.format(Locale.ROOT, "%s (%.0f%%)", label, v * 100);
    }
}

package dev.onex.npccoach.ai;

/** Plain numbers describing one fight; filled in by the server-side recorder. */
public record FightSummary(
        String result, Drill drill, double seconds,
        int swings, int hits, int crits, int sprintHits, int hitsBlockedByCoach,
        double damageDealt, double damageTaken, int hitsTaken, int shieldBlocks,
        int heals, int longestCombo, double avgDistance, double strafePercent,
        double sprintPercent, int jumps, double playerHealthLeft, double coachHealthLeft) {

    public double accuracy() {
        return swings == 0 ? 0 : (double) hits / swings;
    }

    public String toPromptText() {
        return String.format(java.util.Locale.ROOT, """
                Result: %s (drill: %s) after %.0f seconds
                Player swings: %d, hits landed: %d (accuracy %.0f%%), critical hits: %d, sprint (knockback) hits: %d
                Player hits blocked by coach's shield: %d
                Damage dealt: %.1f, damage taken: %.1f, hits taken: %d, hits the player blocked with a shield: %d
                Heals/food/potions used: %d
                Longest combo (hits in a row without being hit): %d
                Average distance to coach: %.2f blocks
                Time strafing: %.0f%%, time sprinting: %.0f%%, jumps: %d
                Health left - player: %.1f, coach: %.1f
                """, result, drill.displayName, seconds, swings, hits, accuracy() * 100, crits, sprintHits,
                hitsBlockedByCoach, damageDealt, damageTaken, hitsTaken, shieldBlocks, heals, longestCombo,
                avgDistance, strafePercent, sprintPercent, jumps, playerHealthLeft, coachHealthLeft);
    }
}

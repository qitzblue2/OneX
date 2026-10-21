package dev.onex.npccoach.ai;

import java.util.Locale;

/** Fighting styles the coach can use; each one targets a weakness. */
public enum Drill {
    NORMAL("Normal", "all-round sparring", 1.0, 12, 0.0, false, false, false),
    RUSH("Rush", "defense, spacing and blocking", 1.25, 9, 0.0, false, false, false),
    STRAFE("Strafe", "aim and target tracking", 1.1, 12, 0.85, false, false, false),
    SHIELD("Shield", "patience, axes and timing around shields", 0.9, 14, 0.2, true, false, false),
    KITE("Kite", "chasing, sprinting and closing distance", 1.15, 12, 0.3, false, true, false),
    COMBO("Combo", "trading hits, crits and keeping combos", 1.05, 10, 0.4, false, false, true);

    public final String displayName;
    public final String trains;
    public final double speed;
    public final int attackCooldown;
    public final double strafeChance;
    public final boolean shield;
    public final boolean retreatAfterHit;
    public final boolean jumpCrits;

    Drill(String displayName, String trains, double speed, int attackCooldown, double strafeChance, boolean shield, boolean retreatAfterHit, boolean jumpCrits) {
        this.displayName = displayName;
        this.trains = trains;
        this.speed = speed;
        this.attackCooldown = attackCooldown;
        this.strafeChance = strafeChance;
        this.shield = shield;
        this.retreatAfterHit = retreatAfterHit;
        this.jumpCrits = jumpCrits;
    }

    public static Drill fromName(String name) {
        if (name == null) return NORMAL;
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (Drill d : values()) if (n.startsWith(d.name())) return d;
        return NORMAL;
    }
}

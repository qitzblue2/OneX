package dev.onex.npccoach.entity;

import dev.onex.npccoach.ai.Drill;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.EnumSet;

/** Melee AI whose style depends on the active drill. */
public class CoachCombatGoal extends Goal {
    private static final double REACH_SQ = 3.0 * 3.0;

    private final CoachNpcEntity coach;
    private int cooldown, retreatTicks, strafeTicks, shieldTicks;
    private float strafeDir = 1f;

    public CoachCombatGoal(CoachNpcEntity coach) {
        this.coach = coach;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() { return coach.sparTarget() != null; }

    @Override
    public boolean shouldContinue() { return coach.sparTarget() != null; }

    @Override
    public boolean shouldRunEveryTick() { return true; }

    @Override
    public void start() {
        cooldown = 20;
        retreatTicks = 0;
    }

    @Override
    public void stop() {
        coach.clearActiveItem();
        coach.getNavigation().stop();
        coach.getMoveControl().strafeTo(0, 0);
    }

    @Override
    public void tick() {
        ServerPlayerEntity target = coach.sparTarget();
        if (target == null) return;
        Drill drill = coach.drill();
        var random = coach.getRandom();
        coach.getLookControl().lookAt(target, 30f, 30f);
        double distSq = coach.squaredDistanceTo(target);
        if (cooldown > 0) cooldown--;

        if (shieldTicks > 0 && --shieldTicks == 0) coach.clearActiveItem();
        if (drill.shield && shieldTicks == 0 && cooldown > 4 && distSq < 16 && random.nextFloat() < 0.08f) {
            coach.setCurrentHand(Hand.OFF_HAND);
            shieldTicks = 15 + random.nextInt(25);
        }

        if (cooldown == 0 && distSq <= REACH_SQ && coach.canSee(target)) {
            if (shieldTicks > 0) {
                coach.clearActiveItem();
                shieldTicks = 0;
            }
            coach.swingHand(Hand.MAIN_HAND);
            coach.tryAttack(target.getEntityWorld(), target);
            cooldown = drill.attackCooldown + random.nextInt(4);
            if (drill.retreatAfterHit) retreatTicks = 12 + random.nextInt(8);
        }

        if (drill.jumpCrits && cooldown <= 6 && cooldown > 3 && distSq < 12 && coach.isOnGround()) {
            coach.getJumpControl().setActive();
        }

        if (retreatTicks > 0) {
            retreatTicks--;
            coach.getNavigation().stop();
            coach.getMoveControl().strafeTo(-0.8f, strafeDir * 0.4f);
            return;
        }

        if (--strafeTicks <= 0) {
            strafeTicks = 20 + random.nextInt(30);
            if (random.nextFloat() < 0.5f) strafeDir = -strafeDir;
        }

        if (distSq > REACH_SQ * 0.8) {
            coach.getNavigation().startMovingTo(target, drill.speed);
        } else if (random.nextFloat() < drill.strafeChance || drill.strafeChance > 0.5) {
            coach.getNavigation().stop();
            coach.getMoveControl().strafeTo(0.15f, strafeDir * 0.7f);
        } else {
            coach.getNavigation().stop();
        }
    }
}

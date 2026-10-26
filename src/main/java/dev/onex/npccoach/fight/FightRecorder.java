package dev.onex.npccoach.fight;

import dev.onex.npccoach.ai.Drill;
import dev.onex.npccoach.ai.FightSummary;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Collects combat telemetry for one player during one match. */
public final class FightRecorder {
    private int ticks, swings, hits, crits, sprintHits, hitsBlockedByCoach, hitsTaken, shieldBlocks, heals;
    private int combo, longestCombo, strafeTicks, sprintTicks, jumps;
    private double damageDealt, damageTaken, distanceSum;
    private Vec3d lastPos;
    private boolean lastOnGround = true, wasUsingItem;

    public void onSwing() { swings++; }

    public void onPlayerHitCoach(ServerPlayerEntity player, float amount, boolean blocked) {
        if (blocked) {
            hitsBlockedByCoach++;
            return;
        }
        hits++;
        damageDealt += amount;
        combo++;
        longestCombo = Math.max(longestCombo, combo);
        if (player.isSprinting()) sprintHits++;
        if (player.fallDistance > 0 && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater() && !player.isSprinting()) crits++;
    }

    public void onCoachHitPlayer(float amount, boolean blocked) {
        if (blocked) {
            shieldBlocks++;
            return;
        }
        hitsTaken++;
        damageTaken += amount;
        combo = 0;
    }

    public void tick(ServerPlayerEntity player, LivingEntity coach) {
        ticks++;
        Vec3d pos = player.getEntityPos();
        distanceSum += Math.sqrt(player.squaredDistanceTo(coach));
        if (lastPos != null) {
            Vec3d delta = pos.subtract(lastPos);
            Vec3d toCoach = coach.getEntityPos().subtract(pos).multiply(1, 0, 1);
            if (toCoach.lengthSquared() > 1.0E-4) {
                Vec3d dir = toCoach.normalize();
                double lateral = Math.abs(delta.x * dir.z - delta.z * dir.x);
                if (lateral > 0.08) strafeTicks++;
            }
            if (lastOnGround && !player.isOnGround() && delta.y > 0) jumps++;
        }
        if (player.isSprinting()) sprintTicks++;
        boolean using = player.isUsingItem();
        if (using && !wasUsingItem) {
            ItemStack stack = player.getActiveItem();
            if (stack.contains(DataComponentTypes.FOOD) || stack.isOf(Items.POTION)) heals++;
        }
        wasUsingItem = using;
        lastPos = pos;
        lastOnGround = player.isOnGround();
    }

    public FightSummary finish(String result, Drill drill, float playerHealth, float coachHealth) {
        double samples = Math.max(1, ticks);
        return new FightSummary(result, drill, ticks / 20.0, swings, hits, crits, sprintHits, hitsBlockedByCoach,
                damageDealt, damageTaken, hitsTaken, shieldBlocks, heals, longestCombo, distanceSum / samples,
                strafeTicks * 100 / samples, sprintTicks * 100 / samples, jumps, playerHealth, coachHealth);
    }
}

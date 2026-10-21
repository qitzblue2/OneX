package dev.onex.npccoach.entity;

import dev.onex.npccoach.NpcCoachMod;
import dev.onex.npccoach.ai.Drill;
import dev.onex.npccoach.fight.MatchManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** The sparring coach. Invulnerable and peaceful unless a match is running. */
public class CoachNpcEntity extends PathAwareEntity {
    private ServerPlayerEntity sparTarget;
    private Drill drill = Drill.NORMAL;

    public CoachNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setCustomName(Text.literal("Coach"));
        setCustomNameVisible(true);
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
        equipFor(Drill.NORMAL);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new CoachCombatGoal(this));
        goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(6, new LookAroundGoal(this));
    }

    public ServerPlayerEntity sparTarget() { return sparTarget; }
    public Drill drill() { return drill; }

    public void prepareSpar() {
        setHealth(getMaxHealth());
        getNavigation().stop();
    }

    public void startSpar(ServerPlayerEntity player, Drill drill) {
        this.sparTarget = player;
        this.drill = drill;
        equipFor(drill);
        setHealth(getMaxHealth());
    }

    public void stopSpar() {
        sparTarget = null;
        clearActiveItem();
        getNavigation().stop();
        setHealth(getMaxHealth());
        equipFor(Drill.NORMAL);
    }

    private void equipFor(Drill drill) {
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(drill == Drill.RUSH ? Items.IRON_SWORD : Items.STONE_SWORD));
        equipStack(EquipmentSlot.OFFHAND, drill == Drill.SHIELD ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (getWorld().isClient || sparTarget == null || source.getAttacker() != sparTarget) return false;
        MatchManager matches = NpcCoachMod.matches();
        MatchManager.Match match = matches.byCoach(this);
        if (match == null || !match.isLive()) return false;

        boolean blocked = isBlocking() && blockedByShield(source);
        match.recorder.onPlayerHitCoach(sparTarget, amount, blocked);
        boolean hurt = super.damage(source, amount);
        if (getHealth() <= 1f) {
            setHealth(1f);
            matches.end(match, "WIN");
        }
        return hurt;
    }

    @Override
    public void setHealth(float health) {
        // Never actually die while sparring; the match ends instead.
        super.setHealth(sparTarget != null ? Math.max(1f, health) : health);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        if (player.isSneaking() && player.getMainHandStack().isEmpty() && sparTarget == null) {
            dropStack(new ItemStack(NpcCoachMod.COACH_SPAWN_EGG));
            discard();
            NpcCoachMod.say(serverPlayer, "See you next time!");
            return ActionResult.SUCCESS;
        }
        NpcCoachMod.sendState(serverPlayer, this, true);
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public boolean isPushable() { return sparTarget != null; }
}

package dev.onex.npccoach.fight;

import dev.onex.npccoach.NpcCoachMod;
import dev.onex.npccoach.ai.CoachReport;
import dev.onex.npccoach.ai.Drill;
import dev.onex.npccoach.ai.FightSummary;
import dev.onex.npccoach.entity.CoachNpcEntity;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MatchManager {
    private static final int COUNTDOWN_TICKS = 60;
    private static final int MAX_TICKS = 20 * 180;
    private static final double MAX_DISTANCE_SQ = 40 * 40;

    public static final class Match {
        public final ServerPlayerEntity player;
        public final CoachNpcEntity coach;
        public final Drill drill;
        public final FightRecorder recorder = new FightRecorder();
        private int age;

        Match(ServerPlayerEntity player, CoachNpcEntity coach, Drill drill) {
            this.player = player;
            this.coach = coach;
            this.drill = drill;
        }

        public boolean isLive() { return age >= COUNTDOWN_TICKS; }
    }

    private final Map<UUID, Match> byPlayer = new HashMap<>();

    public Match get(ServerPlayerEntity player) { return byPlayer.get(player.getUuid()); }

    public Match byCoach(CoachNpcEntity coach) {
        for (Match m : byPlayer.values()) if (m.coach == coach) return m;
        return null;
    }

    public boolean start(ServerPlayerEntity player, CoachNpcEntity coach, Drill drill) {
        if (byPlayer.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("You're already in a fight.").formatted(Formatting.RED), false);
            return false;
        }
        if (byCoach(coach) != null) {
            player.sendMessage(Text.literal("This coach is busy with someone else.").formatted(Formatting.RED), false);
            return false;
        }
        byPlayer.put(player.getUuid(), new Match(player, coach, drill));
        coach.prepareSpar();
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 5));
        NpcCoachMod.say(player, "Drill: " + drill.displayName + " (trains " + drill.trains + "). Get ready!");
        return true;
    }

    public void tick(MinecraftServer server) {
        for (Match m : byPlayer.values().toArray(Match[]::new)) {
            if (m.player.isRemoved() || m.player.isDisconnected() || m.coach.isRemoved() || !m.coach.isAlive()
                    || m.player.getWorld() != m.coach.getWorld() || m.player.squaredDistanceTo(m.coach) > MAX_DISTANCE_SQ) {
                abort(m, "Fight cancelled.");
                continue;
            }
            m.age++;
            if (m.age <= COUNTDOWN_TICKS) {
                int left = COUNTDOWN_TICKS - m.age;
                if (left % 20 == 0) {
                    Text title = left == 0 ? Text.literal("FIGHT!").formatted(Formatting.RED, Formatting.BOLD)
                            : Text.literal(String.valueOf(left / 20)).formatted(Formatting.GOLD);
                    m.player.networkHandler.sendPacket(new TitleS2CPacket(title));
                }
                if (left == 0) m.coach.startSpar(m.player, m.drill);
                continue;
            }
            m.recorder.tick(m.player, m.coach);
            if (m.age - COUNTDOWN_TICKS >= MAX_TICKS) end(m, "TIMEOUT");
        }
    }

    public void onSwing(ServerPlayerEntity player) {
        Match m = get(player);
        if (m != null && m.isLive()) m.recorder.onSwing();
    }

    public void abort(Match m, String reason) {
        byPlayer.remove(m.player.getUuid());
        m.coach.stopSpar();
        if (!m.player.isDisconnected()) m.player.sendMessage(Text.literal(reason).formatted(Formatting.GRAY), false);
    }

    public void abortAll() {
        for (Match m : byPlayer.values().toArray(Match[]::new)) abort(m, "Fight cancelled.");
    }

    /** result is WIN/LOSS/TIMEOUT from the player's point of view. */
    public void end(Match m, String result) {
        if (byPlayer.remove(m.player.getUuid()) == null) return;
        FightSummary summary = m.recorder.finish(result, m.drill, m.player.getHealth(), m.coach.getHealth());
        m.coach.stopSpar();
        ServerPlayerEntity player = m.player;
        Text title = switch (result) {
            case "WIN" -> Text.literal("You win!").formatted(Formatting.GREEN, Formatting.BOLD);
            case "LOSS" -> Text.literal("Coach wins").formatted(Formatting.RED, Formatting.BOLD);
            default -> Text.literal("Time!").formatted(Formatting.YELLOW, Formatting.BOLD);
        };
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        NpcCoachMod.say(player, "Good fight. Let me think about that one...");
        MinecraftServer server = player.getServer();
        NpcCoachMod.brain().analyze(player.getUuid(), summary).thenAccept(report -> server.execute(() -> deliver(player, report)));
    }

    private static void deliver(ServerPlayerEntity player, CoachReport report) {
        if (player.isDisconnected()) return;
        NpcCoachMod.say(player, report.summary());
        for (String s : report.strengths()) player.sendMessage(Text.literal("  + " + s).formatted(Formatting.GREEN), false);
        for (String s : report.weaknesses()) player.sendMessage(Text.literal("  - " + s).formatted(Formatting.RED), false);
        for (String s : report.tips()) player.sendMessage(Text.literal("  > " + s).formatted(Formatting.AQUA), false);
        player.sendMessage(Text.literal("  Recommended drill: " + report.recommendedDrill().displayName
                + " - press K or right-click me to start it.").formatted(Formatting.YELLOW), false);
        NpcCoachMod.sendState(player, null, false);
    }
}

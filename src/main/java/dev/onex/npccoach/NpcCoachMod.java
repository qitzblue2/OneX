package dev.onex.npccoach;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.onex.npccoach.ai.ChatMessage;
import dev.onex.npccoach.ai.CoachBrain;
import dev.onex.npccoach.ai.Drill;
import dev.onex.npccoach.entity.CoachNpcEntity;
import dev.onex.npccoach.fight.MatchManager;
import dev.onex.npccoach.net.CoachActionC2S;
import dev.onex.npccoach.net.CoachStateS2C;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class NpcCoachMod implements ModInitializer {
    public static final String MOD_ID = "npccoach";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final double NEARBY = 16;

    public static final EntityType<CoachNpcEntity> COACH = Registry.register(Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "coach"),
            EntityType.Builder.create(CoachNpcEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).maxTrackingRange(10).build("coach"));
    public static final Item COACH_SPAWN_EGG = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "coach_spawn_egg"),
            new SpawnEggItem(COACH, 0x2B6CB0, 0xF6AD55, new Item.Settings()));

    private static CoachConfig config;
    private static CoachBrain brain;
    private static final MatchManager MATCHES = new MatchManager();

    public static CoachConfig config() { return config; }
    public static CoachBrain brain() { return brain; }
    public static MatchManager matches() { return MATCHES; }

    @Override
    public void onInitialize() {
        config = CoachConfig.load();
        brain = new CoachBrain(config);
        FabricDefaultAttributeRegistry.register(COACH, CoachNpcEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(COACH_SPAWN_EGG));

        PayloadTypeRegistry.playC2S().register(CoachActionC2S.ID, CoachActionC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(CoachStateS2C.ID, CoachStateS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CoachActionC2S.ID, (payload, context) ->
                context.player().server.execute(() -> handleAction(context.player(), payload)));

        ServerTickEvents.END_SERVER_TICK.register(MATCHES::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MATCHES.abortAll());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            MatchManager.Match m = MATCHES.get(handler.player);
            if (m != null) MATCHES.abort(m, "Fight cancelled.");
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            MatchManager.Match m = MATCHES.get(player);
            if (m == null || source.getAttacker() != m.coach) return true;
            if (!m.isLive()) return false;
            boolean blocked = player.isBlocking() && player.blockedByShield(source);
            m.recorder.onCoachHitPlayer(amount, blocked);
            if (!blocked && player.getHealth() - amount <= 1f) {
                MATCHES.end(m, "LOSS");
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            MatchManager.Match m = MATCHES.get(player);
            if (m == null || !m.isLive()) return true;
            player.setHealth(1f);
            MATCHES.end(m, "LOSS");
            return false;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("npccoach")
                        .then(CommandManager.literal("menu").executes(ctx -> run(ctx, "open", "")))
                        .then(CommandManager.literal("start").executes(ctx -> run(ctx, "start", "NORMAL"))
                                .then(CommandManager.argument("drill", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (Drill d : Drill.values()) b.suggest(d.name().toLowerCase());
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> run(ctx, "start", StringArgumentType.getString(ctx, "drill")))))
                        .then(CommandManager.literal("ask").then(CommandManager.argument("question", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, "ask", StringArgumentType.getString(ctx, "question")))))
                        .then(CommandManager.literal("stop").executes(ctx -> run(ctx, "stop", "")))
                        .then(CommandManager.literal("key").requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.argument("keys", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, "setkey", StringArgumentType.getString(ctx, "keys")))))
                        .then(CommandManager.literal("status").executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("NPC-API-KEY: " + config.keyRing().describe()), false);
                            return 1;
                        }))));

        LOGGER.info("NPC Coach ready. NPC-API-KEY: {}", config.keyRing().describe());
    }

    private static int run(CommandContext<ServerCommandSource> ctx, String action, String arg) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        handleAction(ctx.getSource().getPlayerOrThrow(), new CoachActionC2S(action, -1, arg));
        return 1;
    }

    public static boolean canEditKey(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || player.server.isHost(player.getGameProfile());
    }

    private static CoachNpcEntity findCoach(ServerPlayerEntity player, int id) {
        if (id >= 0) {
            Entity e = player.getServerWorld().getEntityById(id);
            if (e instanceof CoachNpcEntity coach && coach.isAlive() && coach.squaredDistanceTo(player) < NEARBY * NEARBY) return coach;
        }
        return player.getServerWorld()
                .getEntitiesByClass(CoachNpcEntity.class, player.getBoundingBox().expand(NEARBY), Entity::isAlive)
                .stream().min(Comparator.comparingDouble(c -> c.squaredDistanceTo(player))).orElse(null);
    }

    static void handleAction(ServerPlayerEntity player, CoachActionC2S action) {
        switch (action.action()) {
            case "open" -> {
                CoachNpcEntity coach = findCoach(player, action.coachId());
                if (coach == null) {
                    player.sendMessage(Text.literal("No coach nearby. Craft a Coach Spawn Egg (stone sword + book + emerald).").formatted(Formatting.GRAY), false);
                } else {
                    sendState(player, coach, true);
                }
            }
            case "start" -> {
                CoachNpcEntity coach = findCoach(player, action.coachId());
                if (coach == null) {
                    player.sendMessage(Text.literal("Stand near a coach to start a fight.").formatted(Formatting.RED), false);
                    return;
                }
                MATCHES.start(player, coach, Drill.fromName(action.arg()));
            }
            case "stop" -> {
                MatchManager.Match m = MATCHES.get(player);
                if (m != null) MATCHES.abort(m, "You stopped the fight.");
            }
            case "ask" -> {
                String question = action.arg().trim();
                if (question.isEmpty()) return;
                if (question.length() > 300) question = question.substring(0, 300);
                if (MATCHES.get(player) != null) {
                    say(player, "Focus on the fight! Ask me afterwards.");
                    return;
                }
                player.sendMessage(Text.literal("You: " + question).formatted(Formatting.GRAY), false);
                brain.ask(player.getUuid(), question).thenAccept(answer -> player.server.execute(() -> {
                    say(player, answer);
                    sendState(player, null, false);
                }));
            }
            case "setkey" -> {
                if (!canEditKey(player)) {
                    player.sendMessage(Text.literal("Only operators can change the NPC-API-KEY.").formatted(Formatting.RED), false);
                    return;
                }
                config.setApiKey(action.arg());
                player.sendMessage(Text.literal("NPC-API-KEY saved: " + config.keyRing().describe()).formatted(Formatting.GREEN), false);
                sendState(player, null, false);
            }
            default -> LOGGER.debug("Unknown coach action {}", action.action());
        }
    }

    public static void say(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal("[Coach] ").formatted(Formatting.GOLD).append(Text.literal(message).formatted(Formatting.WHITE)), false);
    }

    public static void sendState(ServerPlayerEntity player, CoachNpcEntity coach, boolean open) {
        if (!ServerPlayNetworking.canSend(player, CoachStateS2C.ID)) return;
        CoachBrain.Memory memory = brain.memory(player.getUuid());
        String report;
        String drill;
        StringBuilder chat = new StringBuilder();
        synchronized (memory) {
            report = memory.lastReport == null ? "No fights yet. Press Start Fight!" : memory.lastReport.toPlainText();
            drill = memory.lastReport == null ? Drill.NORMAL.name() : memory.lastReport.recommendedDrill().name();
            for (ChatMessage m : memory.history) {
                chat.append(m.role().equals("user") ? "You: " : "Coach: ").append(m.content()).append("\n\n");
            }
        }
        ServerPlayNetworking.send(player, new CoachStateS2C(open, coach == null ? -1 : coach.getId(), report, drill,
                config.keyRing().describe(), canEditKey(player), chat.toString()));
    }
}

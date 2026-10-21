package dev.onex.npccoach.ai;

import dev.onex.npccoach.CoachConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player memory (last fight, last report, chat history) plus the calls to the LLM. */
public final class CoachBrain {
    private static final int MAX_HISTORY = 8;

    public static final class Memory {
        public FightSummary lastFight;
        public CoachReport lastReport;
        public final List<ChatMessage> history = new ArrayList<>();
    }

    private final CoachConfig config;
    private final Map<UUID, Memory> memories = new ConcurrentHashMap<>();

    public CoachBrain(CoachConfig config) {
        this.config = config;
    }

    public Memory memory(UUID player) {
        return memories.computeIfAbsent(player, id -> new Memory());
    }

    /** Never fails: falls back to the offline coach when no key works. */
    public CompletableFuture<CoachReport> analyze(UUID player, FightSummary fight) {
        Memory memory = memory(player);
        KeyRing ring = config.keyRing();
        CompletableFuture<CoachReport> future;
        if (ring.isEmpty()) {
            future = CompletableFuture.completedFuture(OfflineCoach.analyze(fight, "no NPC-API-KEY set"));
        } else {
            String[] used = new String[1];
            future = ring.call(key -> {
                used[0] = key.provider().displayName;
                return LlmClient.chat(key.provider(), key.value(), config.modelFor(key), CoachPrompts.REPORT_SYSTEM, CoachPrompts.reportMessages(fight));
            }).thenApply(text -> CoachReport.parse(text, used[0]))
              .exceptionally(error -> OfflineCoach.analyze(fight, rootMessage(error)));
        }
        return future.thenApply(report -> {
            synchronized (memory) {
                memory.lastFight = fight;
                memory.lastReport = report;
                memory.history.clear();
            }
            config.saveUsage();
            return report;
        });
    }

    public CompletableFuture<String> ask(UUID player, String question) {
        Memory memory = memory(player);
        KeyRing ring = config.keyRing();
        if (ring.isEmpty()) {
            return CompletableFuture.completedFuture("I need an NPC-API-KEY to chat. Ask an admin to add one in the Coach menu.");
        }
        List<ChatMessage> messages;
        synchronized (memory) {
            messages = CoachPrompts.chatMessages(memory.lastFight, memory.lastReport, List.copyOf(memory.history), question);
        }
        return ring.call(key -> LlmClient.chat(key.provider(), key.value(), config.modelFor(key), CoachPrompts.CHAT_SYSTEM, messages))
                .thenApply(answer -> {
                    synchronized (memory) {
                        memory.history.add(ChatMessage.user(question));
                        memory.history.add(ChatMessage.assistant(answer));
                        while (memory.history.size() > MAX_HISTORY) memory.history.remove(0);
                    }
                    config.saveUsage();
                    return answer.trim();
                })
                .exceptionally(error -> "I can't think right now (" + rootMessage(error) + ").");
    }

    private static String rootMessage(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null) t = t.getCause();
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : (msg.length() > 120 ? msg.substring(0, 120) : msg);
    }
}

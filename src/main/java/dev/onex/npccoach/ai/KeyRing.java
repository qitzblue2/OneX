package dev.onex.npccoach.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Parses the single NPC-API-KEY field (one or more keys separated by commas, spaces or newlines),
 * detects each key's provider and rotates to the next key when one fails or hits its daily limit.
 */
public final class KeyRing {
    public record Key(Provider provider, String value) {
        /** Short, non-secret id used for usage tracking and display. */
        public String id() {
            return provider.name() + ":" + value.substring(Math.max(0, value.length() - 4));
        }
    }

    private final List<Key> keys;
    private final List<String> unrecognized;
    private final int dailyLimitPerKey;
    private final Map<String, Integer> usage;
    private LocalDate usageDay;

    public KeyRing(String field, int dailyLimitPerKey, LocalDate usageDay, Map<String, Integer> usage) {
        this.keys = new ArrayList<>();
        this.unrecognized = new ArrayList<>();
        this.dailyLimitPerKey = dailyLimitPerKey;
        this.usageDay = usageDay;
        this.usage = new HashMap<>(usage);
        if (field != null) {
            for (String raw : field.split("[,;\\s]+")) {
                String k = raw.trim();
                if (k.isEmpty()) continue;
                Provider p = Provider.detect(k);
                if (p == null) unrecognized.add(k.length() > 4 ? "..." + k.substring(k.length() - 4) : k);
                else keys.add(new Key(p, k));
            }
        }
    }

    public List<Key> keys() { return keys; }
    public List<String> unrecognized() { return unrecognized; }
    public boolean isEmpty() { return keys.isEmpty(); }

    public synchronized LocalDate usageDay() { return usageDay; }
    public synchronized Map<String, Integer> usageSnapshot() { return new HashMap<>(usage); }

    public synchronized int used(Key key) {
        rollDay();
        return usage.getOrDefault(key.id(), 0);
    }

    public synchronized boolean hasCapacity() {
        for (Key k : keys) if (used(k) < dailyLimitPerKey) return true;
        return false;
    }

    private void rollDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(usageDay)) {
            usageDay = today;
            usage.clear();
        }
    }

    private synchronized List<Key> available() {
        List<Key> out = new ArrayList<>();
        for (Key k : keys) if (used(k) < dailyLimitPerKey) out.add(k);
        return out;
    }

    private synchronized void record(Key key) {
        rollDay();
        usage.merge(key.id(), 1, Integer::sum);
    }

    /** Tries each key with remaining quota in order until one succeeds. */
    public CompletableFuture<String> call(Function<Key, CompletableFuture<String>> request) {
        List<Key> candidates = available();
        if (candidates.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    keys.isEmpty() ? "No NPC-API-KEY configured" : "Daily limit reached on all keys"));
        }
        return attempt(candidates, 0, request, null);
    }

    private CompletableFuture<String> attempt(List<Key> candidates, int index, Function<Key, CompletableFuture<String>> request, Throwable last) {
        if (index >= candidates.size()) return CompletableFuture.failedFuture(last);
        Key key = candidates.get(index);
        CompletableFuture<String> future;
        try {
            future = request.apply(key);
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.thenApply(text -> {
            record(key);
            return text;
        }).exceptionallyCompose(error -> attempt(candidates, index + 1, request, error));
    }

    public String describe() {
        if (keys.isEmpty()) return unrecognized.isEmpty() ? "No key set (offline coaching)" : "Unrecognized key format";
        StringBuilder sb = new StringBuilder();
        for (Key k : keys) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(k.provider().displayName).append(" ").append(used(k)).append('/').append(dailyLimitPerKey);
        }
        if (!unrecognized.isEmpty()) sb.append(" (+").append(unrecognized.size()).append(" unrecognized)");
        return sb.toString();
    }
}

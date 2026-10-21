package dev.onex.npccoach.ai;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class KeyRingTest {
    @Test
    void detectsProvidersFromOneField() {
        KeyRing ring = new KeyRing("gsk_aaaa, sk-ant-bbbb\nsk-proj-cccc AIzadddd junk123", 50, LocalDate.now(), Map.of());
        assertEquals(List.of(Provider.GROQ, Provider.CLAUDE, Provider.OPENAI, Provider.GEMINI),
                ring.keys().stream().map(KeyRing.Key::provider).toList());
        assertEquals(1, ring.unrecognized().size());
    }

    @Test
    void fallsBackToNextKeyOnFailure() {
        KeyRing ring = new KeyRing("gsk_one,gsk_two", 50, LocalDate.now(), Map.of());
        List<String> tried = new ArrayList<>();
        String result = ring.call(key -> {
            tried.add(key.value());
            return key.value().equals("gsk_one")
                    ? CompletableFuture.failedFuture(new LlmClient.LlmException(429, "rate limited"))
                    : CompletableFuture.completedFuture("ok");
        }).join();
        assertEquals("ok", result);
        assertEquals(List.of("gsk_one", "gsk_two"), tried);
        assertEquals(0, ring.used(ring.keys().get(0)));
        assertEquals(1, ring.used(ring.keys().get(1)));
    }

    @Test
    void respectsDailyLimit() {
        KeyRing ring = new KeyRing("gsk_one", 1, LocalDate.now(), Map.of());
        ring.call(k -> CompletableFuture.completedFuture("a")).join();
        assertFalse(ring.hasCapacity());
        assertTrue(ring.call(k -> CompletableFuture.completedFuture("b")).isCompletedExceptionally());
    }

    @Test
    void usageResetsOnNewDay() {
        KeyRing ring = new KeyRing("gsk_one", 1, LocalDate.now().minusDays(1), Map.of("GROQ:_one", 1));
        assertTrue(ring.hasCapacity());
    }
}

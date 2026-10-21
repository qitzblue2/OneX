package dev.onex.npccoach.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoachReportTest {
    @Test
    void parsesJsonInsideCodeFence() {
        String text = "```json\n{\"summary\":\"Close fight\",\"strengths\":[\"aim\"],\"weaknesses\":[\"no strafing\"],"
                + "\"tips\":[\"hold A/D\"],\"recommended_drill\":\"strafe\"}\n```";
        CoachReport r = CoachReport.parse(text, "Groq");
        assertEquals("Close fight", r.summary());
        assertEquals(Drill.STRAFE, r.recommendedDrill());
        assertEquals(1, r.weaknesses().size());
    }

    @Test
    void plainTextFallback() {
        CoachReport r = CoachReport.parse("You did fine.", "Groq");
        assertEquals("You did fine.", r.summary());
        assertEquals(Drill.NORMAL, r.recommendedDrill());
    }

    @Test
    void extractsTextFromEachProvider() {
        assertEquals("hi", LlmClient.extractText(Provider.GROQ, "{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}"));
        assertEquals("hi", LlmClient.extractText(Provider.CLAUDE, "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}"));
        assertEquals("hi", LlmClient.extractText(Provider.GEMINI, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hi\"}]}}]}"));
    }

    @Test
    void offlineCoachRecommendsDrill() {
        FightSummary f = new FightSummary("LOSS", Drill.NORMAL, 40, 30, 8, 0, 2, 0, 20, 19, 15, 0, 0, 2, 3.0, 5, 40, 3, 1, 12);
        CoachReport r = OfflineCoach.analyze(f, "test");
        assertFalse(r.weaknesses().isEmpty());
        assertNotEquals(Drill.NORMAL, r.recommendedDrill());
    }
}

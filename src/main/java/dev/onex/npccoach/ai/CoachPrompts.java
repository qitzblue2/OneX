package dev.onex.npccoach.ai;

import java.util.ArrayList;
import java.util.List;

public final class CoachPrompts {
    private CoachPrompts() {}

    public static String drillList() {
        StringBuilder sb = new StringBuilder();
        for (Drill d : Drill.values()) sb.append("- ").append(d.name()).append(": trains ").append(d.trains).append('\n');
        return sb.toString();
    }

    public static final String REPORT_SYSTEM = """
            You are "Coach", a friendly but honest Minecraft 1.21 PvP sparring partner. The player just fought you.
            Analyze the fight statistics and help them improve. Be specific and refer to the numbers.
            Minecraft 1.21 combat facts: attack cooldown matters (wait for a full swing), crits need falling hits,
            sprint hits give extra knockback, strafing makes you harder to hit, shields block until disabled by an axe.
            Answer ONLY with JSON in this exact shape, no other text:
            {"summary": "1-2 sentences", "strengths": ["..."], "weaknesses": ["..."], "tips": ["concrete practice tip", "..."], "recommended_drill": "ONE_OF_THE_DRILLS"}
            Keep each list to 1-3 short items (under 20 words each). Pick the drill that targets the biggest weakness:
            """ + drillList();

    public static final String CHAT_SYSTEM = """
            You are "Coach", a friendly Minecraft 1.21 PvP sparring partner. You fought this player and analyzed the fight.
            Answer their question in at most 4 short sentences, plain text, no markdown. Stay on Minecraft combat and
            practice advice. If they ask which drill to do, choose from:
            """ + drillList();

    public static List<ChatMessage> reportMessages(FightSummary fight) {
        return List.of(ChatMessage.user("Fight statistics:\n" + fight.toPromptText()));
    }

    public static List<ChatMessage> chatMessages(FightSummary fight, CoachReport report, List<ChatMessage> history, String question) {
        List<ChatMessage> out = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        if (fight != null) context.append("Last fight statistics:\n").append(fight.toPromptText());
        else context.append("We have not fought yet.\n");
        if (report != null) context.append("\nYour report:\n").append(report.toPlainText());
        out.add(ChatMessage.user(context.toString()));
        out.add(ChatMessage.assistant("Got it. What do you want to know?"));
        out.addAll(history);
        out.add(ChatMessage.user(question));
        return out;
    }
}

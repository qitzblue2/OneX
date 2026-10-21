package dev.onex.npccoach.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record CoachReport(String summary, List<String> strengths, List<String> weaknesses, List<String> tips, Drill recommendedDrill, String source) {

    /** Parses the model's JSON answer; tolerates code fences and surrounding prose. */
    public static CoachReport parse(String text, String source) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
                return new CoachReport(
                        str(o, "summary"),
                        list(o, "strengths"),
                        list(o, "weaknesses"),
                        list(o, "tips"),
                        Drill.fromName(str(o, "recommended_drill")),
                        source);
            } catch (RuntimeException ignored) {
                // fall through to plain text
            }
        }
        return new CoachReport(text.trim(), List.of(), List.of(), List.of(), Drill.NORMAL, source);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static List<String> list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e instanceof JsonArray arr) for (JsonElement item : arr) out.add(item.getAsString());
        else if (e != null && !e.isJsonNull()) out.add(e.getAsString());
        return out;
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder(summary);
        if (!strengths.isEmpty()) sb.append("\n\nGood:");
        for (String s : strengths) sb.append("\n + ").append(s);
        if (!weaknesses.isEmpty()) sb.append("\n\nWork on:");
        for (String s : weaknesses) sb.append("\n - ").append(s);
        if (!tips.isEmpty()) sb.append("\n\nTips:");
        for (String s : tips) sb.append("\n > ").append(s);
        sb.append("\n\nRecommended drill: ").append(recommendedDrill.displayName).append(" (").append(recommendedDrill.trains).append(")");
        sb.append("\n[").append(source).append("]");
        return sb.toString();
    }
}

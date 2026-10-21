package dev.onex.npccoach;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import dev.onex.npccoach.ai.KeyRing;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** config/npccoach.json. The only thing most people need to touch is NPC-API-KEY. */
public final class CoachConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("npccoach.json");
    private static final Path USAGE_FILE = FabricLoader.getInstance().getConfigDir().resolve("npccoach-usage.json");

    @SerializedName("NPC-API-KEY")
    public String apiKey = "";
    public int dailyLimitPerKey = 50;
    public String groqModel = "llama-3.1-8b-instant";
    public String openaiModel = "gpt-4o-mini";
    public String claudeModel = "claude-haiku-5-5";
    public String geminiModel = "gemini-2.5-flash";

    private transient KeyRing keyRing;

    public static CoachConfig load() {
        CoachConfig config = new CoachConfig();
        try {
            if (Files.exists(FILE)) config = GSON.fromJson(Files.readString(FILE), CoachConfig.class);
        } catch (IOException | RuntimeException e) {
            NpcCoachMod.LOGGER.error("Could not read {}, using defaults", FILE, e);
        }
        if (config == null) config = new CoachConfig();
        String env = System.getenv("NPC_API_KEY");
        if ((config.apiKey == null || config.apiKey.isBlank()) && env != null) config.apiKey = env;
        config.save();
        config.rebuildKeyRing();
        return config;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            NpcCoachMod.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    public synchronized void setApiKey(String value) {
        apiKey = value == null ? "" : value.trim();
        save();
        rebuildKeyRing();
    }

    public synchronized KeyRing keyRing() {
        return keyRing;
    }

    public String modelFor(KeyRing.Key key) {
        return switch (key.provider()) {
            case GROQ -> groqModel;
            case OPENAI -> openaiModel;
            case CLAUDE -> claudeModel;
            case GEMINI -> geminiModel;
        };
    }

    private void rebuildKeyRing() {
        LocalDate day = LocalDate.now();
        Map<String, Integer> usage = new HashMap<>();
        try {
            if (Files.exists(USAGE_FILE)) {
                JsonObject o = GSON.fromJson(Files.readString(USAGE_FILE), JsonObject.class);
                LocalDate saved = LocalDate.parse(o.get("day").getAsString());
                if (saved.equals(day)) {
                    for (var e : o.getAsJsonObject("counts").entrySet()) usage.put(e.getKey(), e.getValue().getAsInt());
                }
            }
        } catch (IOException | RuntimeException e) {
            NpcCoachMod.LOGGER.warn("Could not read usage file, starting fresh", e);
        }
        keyRing = new KeyRing(apiKey, dailyLimitPerKey, day, usage);
    }

    public void saveUsage() {
        KeyRing ring = keyRing();
        JsonObject o = new JsonObject();
        o.addProperty("day", ring.usageDay().toString());
        JsonObject counts = new JsonObject();
        ring.usageSnapshot().forEach(counts::addProperty);
        o.add("counts", counts);
        try {
            Files.writeString(USAGE_FILE, GSON.toJson(o));
        } catch (IOException e) {
            NpcCoachMod.LOGGER.warn("Could not save usage file", e);
        }
    }
}

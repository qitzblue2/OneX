package dev.onex.npccoach.ai;

/** LLM providers, detected from the key prefix so the config only needs one key field. */
public enum Provider {
    GROQ("Groq", "gsk_"),
    CLAUDE("Claude", "sk-ant-"),
    GEMINI("Gemini", "AIza"),
    OPENAI("ChatGPT", "sk-");

    public final String displayName;
    private final String prefix;

    Provider(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }

    /** Order matters: "sk-ant-" must be checked before the generic OpenAI "sk-". */
    public static Provider detect(String key) {
        if (key == null) return null;
        for (Provider p : values()) {
            if (key.startsWith(p.prefix)) return p;
        }
        return null;
    }
}

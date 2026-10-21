package dev.onex.npccoach.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Talks to Groq, OpenAI, Claude and Gemini with plain java.net.http. */
public final class LlmClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final int MAX_TOKENS = 700;

    public static final class LlmException extends RuntimeException {
        public final int status;

        public LlmException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private LlmClient() {}

    public static CompletableFuture<String> chat(Provider provider, String key, String model, String system, List<ChatMessage> messages) {
        HttpRequest request = switch (provider) {
            case GROQ -> openAiRequest("https://api.groq.com/openai/v1/chat/completions", key, model, system, messages);
            case OPENAI -> openAiRequest("https://api.openai.com/v1/chat/completions", key, model, system, messages);
            case CLAUDE -> claudeRequest(key, model, system, messages);
            case GEMINI -> geminiRequest(key, model, system, messages);
        };
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                String body = response.body();
                throw new LlmException(response.statusCode(), provider.displayName + " HTTP " + response.statusCode() + ": "
                        + (body.length() > 200 ? body.substring(0, 200) : body));
            }
            return extractText(provider, response.body());
        });
    }

    static String extractText(Provider provider, String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        return switch (provider) {
            case GROQ, OPENAI -> root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            case CLAUDE -> {
                StringBuilder sb = new StringBuilder();
                for (var block : root.getAsJsonArray("content")) {
                    JsonObject b = block.getAsJsonObject();
                    if ("text".equals(b.get("type").getAsString())) sb.append(b.get("text").getAsString());
                }
                yield sb.toString();
            }
            case GEMINI -> {
                StringBuilder sb = new StringBuilder();
                JsonArray parts = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content").getAsJsonArray("parts");
                for (var part : parts) {
                    JsonObject p = part.getAsJsonObject();
                    if (p.has("text")) sb.append(p.get("text").getAsString());
                }
                yield sb.toString();
            }
        };
    }

    private static HttpRequest openAiRequest(String url, String key, String model, String system, List<ChatMessage> messages) {
        JsonArray msgs = new JsonArray();
        msgs.add(message("system", system));
        for (ChatMessage m : messages) msgs.add(message(m.role(), m.content()));
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_TOKENS);
        body.addProperty("temperature", 0.6);
        body.add("messages", msgs);
        return post(url, body).header("Authorization", "Bearer " + key).build();
    }

    private static HttpRequest claudeRequest(String key, String model, String system, List<ChatMessage> messages) {
        JsonArray msgs = new JsonArray();
        for (ChatMessage m : messages) msgs.add(message(m.role(), m.content()));
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_TOKENS);
        body.addProperty("system", system);
        body.add("messages", msgs);
        return post("https://api.anthropic.com/v1/messages", body)
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .build();
    }

    private static HttpRequest geminiRequest(String key, String model, String system, List<ChatMessage> messages) {
        JsonArray contents = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject c = new JsonObject();
            c.addProperty("role", m.role().equals("assistant") ? "model" : "user");
            c.add("parts", textParts(m.content()));
            contents.add(c);
        }
        JsonObject sys = new JsonObject();
        sys.add("parts", textParts(system));
        JsonObject config = new JsonObject();
        config.addProperty("maxOutputTokens", MAX_TOKENS);
        config.addProperty("temperature", 0.6);
        JsonObject body = new JsonObject();
        body.add("systemInstruction", sys);
        body.add("contents", contents);
        body.add("generationConfig", config);
        return post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent", body)
                .header("x-goog-api-key", key)
                .build();
    }

    private static HttpRequest.Builder post(String url, JsonObject body) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    }

    private static JsonObject message(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static JsonArray textParts(String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        return parts;
    }
}

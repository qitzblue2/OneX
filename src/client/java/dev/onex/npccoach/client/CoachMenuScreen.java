package dev.onex.npccoach.client;

import dev.onex.npccoach.ai.Drill;
import dev.onex.npccoach.net.CoachActionC2S;
import dev.onex.npccoach.net.CoachStateS2C;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/** Small menu: start a fight or drill, read the last report, chat with the coach, set the NPC-API-KEY. */
public class CoachMenuScreen extends Screen {
    private static final int PANEL_WIDTH = 320;

    private CoachStateS2C state;
    private Drill selectedDrill;
    private boolean showChat;
    private int scroll;
    private List<OrderedText> lines = List.of();
    private TextFieldWidget askField;
    private TextFieldWidget keyField;

    public CoachMenuScreen(CoachStateS2C state) {
        super(Text.literal("Coach"));
        this.state = state;
        this.selectedDrill = Drill.fromName(state.recommendedDrill());
    }

    public void update(CoachStateS2C newState) {
        boolean chatChanged = !newState.chat().equals(state.chat());
        int coachId = newState.coachId() >= 0 ? newState.coachId() : state.coachId();
        state = new CoachStateS2C(false, coachId, newState.report(), newState.recommendedDrill(),
                newState.keyStatus(), newState.canEditKey(), newState.chat());
        if (chatChanged) showChat = true;
        clearAndInit();
    }

    private int left() { return (width - PANEL_WIDTH) / 2; }

    @Override
    protected void init() {
        int x = left();
        int y = 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Fight"), b -> start(Drill.NORMAL))
                .dimensions(x, y, 100, 20).build());
        addDrawableChild(CyclingButtonWidget.<Drill>builder(d -> Text.literal(d.displayName), selectedDrill)
                .values(Drill.values())
                .build(x + 104, y, 112, 20, Text.literal("Drill"), (button, value) -> selectedDrill = value));
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Drill"), b -> start(selectedDrill))
                .dimensions(x + 220, y, 100, 20).build());

        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Last Report"), b -> { showChat = false; rebuildLines(); })
                .dimensions(x, y, 158, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Chat"), b -> { showChat = true; rebuildLines(); })
                .dimensions(x + 162, y, 158, 20).build());

        int bottom = height - (state.canEditKey() ? 76 : 52);
        askField = new TextFieldWidget(textRenderer, x, bottom, 266, 20, Text.literal("Ask Coach"));
        askField.setMaxLength(300);
        askField.setPlaceholder(Text.literal("Ask the coach something..."));
        addDrawableChild(askField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Ask"), b -> ask()).dimensions(x + 270, bottom, 50, 20).build());

        if (state.canEditKey()) {
            keyField = new TextFieldWidget(textRenderer, x, bottom + 24, 266, 20, Text.literal("NPC-API-KEY"));
            keyField.setMaxLength(2048);
            keyField.setPlaceholder(Text.literal("NPC-API-KEY (keys separated by commas)"));
            keyField.addFormatter((text, index) -> OrderedText.styledForwardsVisitedString("*".repeat(text.length()), Style.EMPTY));
            addDrawableChild(keyField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> saveKey()).dimensions(x + 270, bottom + 24, 50, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close()).dimensions(x + 220, height - 26, 100, 20).build());
        rebuildLines();
    }

    private void rebuildLines() {
        String text = showChat
                ? (state.chat().isEmpty() ? "Ask me anything about your last fight." : state.chat())
                : state.report();
        lines = textRenderer.wrapLines(StringVisitable.plain(text), PANEL_WIDTH - 8);
        scroll = showChat ? Math.max(0, lines.size() - visibleLines()) : 0;
    }

    private int textTop() { return 80; }

    private int visibleLines() {
        int bottom = height - (state.canEditKey() ? 80 : 56);
        return Math.max(1, (bottom - textTop()) / 10);
    }

    private void start(Drill drill) {
        ClientPlayNetworking.send(new CoachActionC2S("start", state.coachId(), drill.name()));
        close();
    }

    private void ask() {
        String q = askField.getText().trim();
        if (q.isEmpty()) return;
        ClientPlayNetworking.send(new CoachActionC2S("ask", state.coachId(), q));
        askField.setText("");
        showChat = true;
        String pending = state.chat() + "You: " + q + "\n\nCoach: ...";
        lines = textRenderer.wrapLines(StringVisitable.plain(pending), PANEL_WIDTH - 8);
        scroll = Math.max(0, lines.size() - visibleLines());
    }

    private void saveKey() {
        ClientPlayNetworking.send(new CoachActionC2S("setkey", state.coachId(), keyField.getText()));
        keyField.setText("");
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 257 && askField.isFocused()) { // Enter
            ask();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount) * 3, 0, Math.max(0, lines.size() - visibleLines()));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = left();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Coach").styled(s -> s.withBold(true)), width / 2, 10, 0xFFFFAA00);

        int top = textTop() - 4;
        int bottom = top + visibleLines() * 10 + 8;
        context.fill(x - 2, top, x + PANEL_WIDTH + 2, bottom, 0x90000000);
        int y = textTop();
        for (int i = scroll; i < lines.size() && i < scroll + visibleLines(); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), x + 2, y, 0xFFFFFFFF);
            y += 10;
        }
        context.drawTextWithShadow(textRenderer, Text.literal("NPC-API-KEY: " + state.keyStatus()), x, height - 20, 0xFFA0A0A0);
    }

    @Override
    public boolean shouldPause() { return false; }
}

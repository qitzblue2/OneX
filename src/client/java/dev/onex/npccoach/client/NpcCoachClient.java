package dev.onex.npccoach.client;

import dev.onex.npccoach.NpcCoachMod;
import dev.onex.npccoach.net.CoachActionC2S;
import dev.onex.npccoach.net.CoachStateS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class NpcCoachClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NpcCoachMod.COACH, CoachRenderer::new);

        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.npccoach.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.create(Identifier.of("npccoach", "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new CoachActionC2S("open", -1, ""));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(CoachStateS2C.ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().currentScreen instanceof CoachMenuScreen screen) screen.update(payload);
            else if (payload.open()) context.client().setScreen(new CoachMenuScreen(payload));
        }));
    }
}

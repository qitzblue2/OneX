package dev.onex.npccoach.mixin;

import dev.onex.npccoach.NpcCoachMod;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts every main-hand swing so we can compute accuracy (hits / swings). */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onHandSwing", at = @At("TAIL"))
    private void npccoach$onHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        if (packet.getHand() == Hand.MAIN_HAND) NpcCoachMod.matches().onSwing(player);
    }
}

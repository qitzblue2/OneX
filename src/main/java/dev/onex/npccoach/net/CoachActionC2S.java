package dev.onex.npccoach.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Menu button pressed. action: open | start | ask | setkey; coachId is -1 for "nearest coach". */
public record CoachActionC2S(String action, int coachId, String arg) implements CustomPayload {
    public static final Id<CoachActionC2S> ID = new Id<>(Identifier.of("npccoach", "action"));
    public static final PacketCodec<RegistryByteBuf, CoachActionC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, CoachActionC2S::action,
            PacketCodecs.VAR_INT, CoachActionC2S::coachId,
            PacketCodecs.string(4096), CoachActionC2S::arg,
            CoachActionC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

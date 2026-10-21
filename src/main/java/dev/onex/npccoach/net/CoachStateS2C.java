package dev.onex.npccoach.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Everything the menu shows. The API key itself is never sent, only its status.
 * open=true asks the client to open the menu; otherwise an open menu just refreshes.
 */
public record CoachStateS2C(boolean open, int coachId, String report, String recommendedDrill,
                            String keyStatus, boolean canEditKey, String chat) implements CustomPayload {
    public static final Id<CoachStateS2C> ID = new Id<>(Identifier.of("npccoach", "state"));
    public static final PacketCodec<RegistryByteBuf, CoachStateS2C> CODEC = PacketCodec.of(CoachStateS2C::write, CoachStateS2C::read);

    private static CoachStateS2C read(RegistryByteBuf buf) {
        return new CoachStateS2C(buf.readBoolean(), buf.readVarInt(), buf.readString(), buf.readString(),
                buf.readString(), buf.readBoolean(), buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeVarInt(coachId);
        buf.writeString(report);
        buf.writeString(recommendedDrill);
        buf.writeString(keyStatus);
        buf.writeBoolean(canEditKey);
        buf.writeString(chat);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}

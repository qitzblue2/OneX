package dev.onex.npccoach.client;

import dev.onex.npccoach.entity.CoachNpcEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Renders the coach with the default Steve player model. */
public class CoachRenderer extends BipedEntityRenderer<CoachNpcEntity, PlayerEntityModel<CoachNpcEntity>> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    public CoachRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(CoachNpcEntity entity) {
        return TEXTURE;
    }
}

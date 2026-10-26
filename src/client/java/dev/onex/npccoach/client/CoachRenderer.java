package dev.onex.npccoach.client;

import dev.onex.npccoach.entity.CoachNpcEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;

/** Renders the coach with the player model and the default Steve skin. */
public class CoachRenderer extends BipedEntityRenderer<CoachNpcEntity, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

    public CoachRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public BipedEntityRenderState createRenderState() {
        return new BipedEntityRenderState();
    }

    @Override
    public Identifier getTexture(BipedEntityRenderState state) {
        return TEXTURE;
    }
}

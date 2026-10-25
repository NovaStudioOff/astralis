package com.astralis.client;

import com.astralis.AstralisMod;
import com.astralis.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RocketRenderer extends EntityRenderer<RocketEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(AstralisMod.MODID, "rocket"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AstralisMod.MODID, "textures/entity/rocket.png");

    private final RocketModel model;

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.model = new RocketModel(context.bakeLayer(LAYER));
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(RocketEntity rocket, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F - yaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        // Rendu avec culling : depuis la cabine, l'intérieur de la coque reste transparent.
        model.renderBody(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, OverlayTexture.NO_OVERLAY);

        if (rocket.isBurning()) {
            poseStack.pushPose();
            float flicker = 0.8F + rocket.level.random.nextFloat() * 0.45F;
            poseStack.translate(0.0D, 1.5D, 0.0D);
            poseStack.scale(1.0F, flicker, 1.0F);
            poseStack.translate(0.0D, -1.5D, 0.0D);
            model.renderFlame(poseStack, buffers.getBuffer(RenderType.eyes(TEXTURE)), 15728880, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();

        super.render(rocket, yaw, partialTick, poseStack, buffers, light);
    }
}

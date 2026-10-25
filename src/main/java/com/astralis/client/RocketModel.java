package com.astralis.client;

import com.astralis.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Modèle de la fusée : ~9,5 blocs de haut (fuselage, bande noire, cabine, ogive rouge, 4 ailerons, tuyère). */
public class RocketModel extends EntityModel<RocketEntity> {
    private final ModelPart body;
    private final ModelPart flame;

    public RocketModel(ModelPart root) {
        this.body = root.getChild("body");
        this.flame = root.getChild("flame");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Origine du corps : au sol (y = 24 px dans le repère du modèle) ; les boîtes montent en y négatif.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        body.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-10.0F, -12.0F, -10.0F, 20.0F, 12.0F, 20.0F), PartPose.ZERO);
        body.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-14.0F, -52.0F, -14.0F, 28.0F, 40.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 100)
                .addBox(-15.0F, -60.0F, -15.0F, 30.0F, 8.0F, 30.0F), PartPose.ZERO);
        body.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(0, 138)
                .addBox(-14.0F, -108.0F, -14.0F, 28.0F, 48.0F, 28.0F), PartPose.ZERO);
        body.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(128, 0)
                .addBox(-13.0F, -124.0F, -13.0F, 26.0F, 16.0F, 26.0F), PartPose.ZERO);
        body.addOrReplaceChild("nose1", CubeListBuilder.create().texOffs(128, 42)
                .addBox(-10.0F, -136.0F, -10.0F, 20.0F, 12.0F, 20.0F), PartPose.ZERO);
        body.addOrReplaceChild("nose2", CubeListBuilder.create().texOffs(128, 74)
                .addBox(-6.0F, -144.0F, -6.0F, 12.0F, 8.0F, 12.0F), PartPose.ZERO);
        body.addOrReplaceChild("nose3", CubeListBuilder.create().texOffs(128, 94)
                .addBox(-3.0F, -152.0F, -3.0F, 6.0F, 8.0F, 6.0F), PartPose.ZERO);

        // Ailerons en gradins : une base large collée au fuselage + une pointe plus basse
        fin(body, "fin_e", 14.0F, -3.0F, 10.0F, 6.0F, 24.0F, -3.0F);
        fin(body, "fin_w", -24.0F, -3.0F, 10.0F, 6.0F, -30.0F, -3.0F);
        fin(body, "fin_s", -3.0F, 14.0F, 6.0F, 10.0F, -3.0F, 24.0F);
        fin(body, "fin_n", -3.0F, -24.0F, 6.0F, 10.0F, -3.0F, -30.0F);

        root.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(128, 160)
                .addBox(-8.0F, 0.0F, -8.0F, 16.0F, 40.0F, 16.0F), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void fin(PartDefinition body, String name, float bx, float bz, float bw, float bd,
                            float tx, float tz) {
        body.addOrReplaceChild(name + "_base", CubeListBuilder.create().texOffs(128, 110)
                .addBox(bx, -48.0F, bz, bw, 36.0F, bd), PartPose.ZERO);
        body.addOrReplaceChild(name + "_tip", CubeListBuilder.create().texOffs(160, 110)
                .addBox(tx, -34.0F, tz, 6.0F, 22.0F, 6.0F), PartPose.ZERO);
    }

    @Override
    public void setupAnim(RocketEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                               float r, float g, float b, float a) {
        body.render(poseStack, buffer, light, overlay, r, g, b, a);
    }

    public void renderBody(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        body.render(poseStack, buffer, light, overlay);
    }

    public void renderFlame(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        flame.render(poseStack, buffer, light, overlay);
    }
}

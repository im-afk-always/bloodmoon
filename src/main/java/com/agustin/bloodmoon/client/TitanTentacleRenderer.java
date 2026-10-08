package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.TitanTentacle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Tentáculo Titánico: se alza, se echa atrás, se desploma a lo largo de la franja marcada y luego se hunde. */
public class TitanTentacleRenderer extends EntityRenderer<TitanTentacle> {
    public TitanTentacleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(TitanTentacle entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(TitanTentacle entity) {
        return VoidEyeRenderer.TENTACLE;
    }

    private static float smooth(float x) {
        return VoidEyeRenderer.smooth(x);
    }

    @Override
    public void render(TitanTentacle e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        float yaw = e.slamYaw();

        // franja de aviso
        if (a < TitanTentacle.SLAM + 3) {
            float urgency = smooth(a / TitanTentacle.SLAM);
            float pulse = 0.55F + 0.45F * Mth.sin(a * (0.2F + 0.9F * urgency));
            float c = pulse * (0.25F + 0.75F * urgency) * (a > TitanTentacle.SLAM ? 1F - (a - TitanTentacle.SLAM) / 3F : 1F);
            boolean locked = a >= TitanTentacle.LOCK;
            ps.pushPose();
            ps.translate(0F, 0.12F, 0F);
            ps.mulPose(Axis.YP.rotationDegrees(90F - yaw));
            float w = TitanTentacle.HALF_WIDTH, L = TitanTentacle.LENGTH;
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WALL));
            VoidEyeRenderer.quad2(ps.last(), vc, -w, 0, 0, w, 0, 0, w, 0, L, -w, 0, L, 0, 1,
                    c, c * (locked ? 0.45F : 0.1F), c * (locked ? 0.9F : 0.55F));
            ps.popPose();
        }

        // el tentáculo
        float len, tilt, sink = 0F;
        if (a < TitanTentacle.EMERGE) {
            len = TitanTentacle.LENGTH * smooth(a / TitanTentacle.EMERGE);
            tilt = 0F;
        } else if (a < TitanTentacle.LOCK) {
            len = TitanTentacle.LENGTH;
            tilt = -14F * smooth((a - TitanTentacle.EMERGE) / 20F) + 3F * Mth.sin(a * 0.12F);
        } else if (a < TitanTentacle.SLAM - 4) {
            len = TitanTentacle.LENGTH;
            tilt = Mth.lerp(smooth((a - TitanTentacle.LOCK) / (TitanTentacle.SLAM - 4 - TitanTentacle.LOCK)), -14F, -28F);
        } else if (a < TitanTentacle.SLAM) {
            len = TitanTentacle.LENGTH;
            float k = (a - (TitanTentacle.SLAM - 4)) / 4F;
            tilt = Mth.lerp(k * k, -28F, 91F);
        } else if (a < TitanTentacle.LIE) {
            len = TitanTentacle.LENGTH;
            tilt = 91F + 2F * Mth.sin((a - TitanTentacle.SLAM) * 0.8F) * Math.max(0F, 1F - (a - TitanTentacle.SLAM) / 12F);
        } else {
            float k = smooth((a - TitanTentacle.LIE) / (TitanTentacle.END - TitanTentacle.LIE));
            len = TitanTentacle.LENGTH * (1F - k);
            tilt = 91F - 50F * k;
            sink = 6F * k;
        }
        if (len < 0.5F) return;
        final float wave = a * 0.05F;
        final boolean lying = a >= TitanTentacle.SLAM;
        ps.pushPose();
        ps.translate(0F, -3F - sink, 0F);
        ps.mulPose(Axis.YP.rotationDegrees(90F - yaw));
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        int overlay = OverlayTexture.NO_OVERLAY;
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            TentacleMesh.render(ps, vc, VoidEyeRenderer.FULL, overlay, len, TitanTentacle.RADIUS, 30, (i, along) -> new float[]{
                    lying ? 0.004F * Mth.sin(wave + i) : 0.03F * Mth.sin(wave * 2 + i * 0.35F) + 0.006F,
                    lying ? 0F : 0.025F * Mth.cos(wave * 1.7F + i * 0.3F)},
                    pass == 0 ? 1F : 0.6F, pass == 0 ? 1F : 0.18F, pass == 0 ? 1F : 0.85F, 1F);
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }
}

package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnboundObserver;
import com.agustin.bloodmoon.entity.VoidEye;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;

/**
 * Lo que hace imponente al Observador Desatado (se dibuja en el marco escalado del Ojo, en unidades del Ojo
 * del Santuario): tres anillos de runas más (uno doble), un segundo cinturón de obsidiana llorosa, un halo
 * vertical de monolitos detrás del Ojo, una cortina de tentáculos colgantes, un aura a sus espaldas y,
 * durante Las Fauces, un remolino negro en la pupila.
 */
final class UnboundExtras {
    private static final int FULL = VoidEyeRenderer.FULL;

    private UnboundExtras() {}

    static void render(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float pt, float t, float age, float k, int overlay,
                       Quaternionf camera) {
        int s = e.getState();
        boolean frenzy = e.getPhase() >= 5 || s == VoidEye.S_SCREAM || s == UnboundObserver.S_COLOSSAL || s == UnboundObserver.S_TEARS;
        float speed = frenzy ? 2.4F : 1F;

        // ---- anillos de runas extra
        float[][] rings = {{22.5F, 38F, 0.2F, 1.5F}, {26F, -24F, -0.32F, 1.1F}, {30F, 86F, 0.14F, 2.2F}, {31.2F, 86F, 0.14F, 0.7F}};
        for (int i = 0; i < rings.length; i++) {
            float[] rg = rings[i];
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.22F * (i + 1) * speed + i * 50));
            ps.mulPose(Axis.XP.rotationDegrees(rg[1] + 5F * Mth.sin(t * 0.012F + i)));
            ps.mulPose(Axis.YP.rotationDegrees(t * rg[2] * speed));
            float b = k * (0.55F + 0.25F * Mth.sin(t * 0.06F + i * 1.7F));
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES));
            VoidEyeRenderer.band(ps.last(), vc, rg[0], rg[3], 96, 14, frenzy ? b : b * 0.75F, b * 0.25F, b, 0F);
            ps.popPose();
        }

        // ---- segundo cinturón: obsidiana llorosa
        if (k > 0.01F) {
            var blocks = Minecraft.getInstance().getBlockRenderer();
            var crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(28F));
            ps.mulPose(Axis.ZP.rotationDegrees(-16F));
            int n = 22;
            for (int i = 0; i < n; i++) {
                float a = Mth.TWO_PI * i / n - t * 0.006F * speed * (1F + (i % 4) * 0.2F);
                float rad = (25.5F + 2F * Mth.sin(i * 1.7F)) * (0.6F + 0.4F * k);
                float sc = (1.1F + 0.9F * ((i * 5) % 4) / 3F) * k;
                ps.pushPose();
                ps.translate(Mth.cos(a) * rad, Mth.sin(t * 0.025F + i) * 2F, Mth.sin(a) * rad);
                ps.mulPose(Axis.XP.rotationDegrees(t * (0.9F + i % 3)));
                ps.mulPose(Axis.ZP.rotationDegrees(t * (0.6F + i % 4) + i * 30));
                ps.scale(sc, sc, sc);
                ps.translate(-0.5F, -0.5F, -0.5F);
                blocks.renderSingleBlock(crying, ps, buf, FULL, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.popPose();

            // ---- halo de monolitos detrás del Ojo
            float yaw = Mth.rotLerp(pt, e.yawO, e.yaw);
            var obsidian = Blocks.OBSIDIAN.defaultBlockState();
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0F, 1.5F, -9F);
            ps.mulPose(Axis.ZP.rotationDegrees(t * 0.35F * speed));
            for (int i = 0; i < 8; i++) {
                float a = 45F * i;
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(a));
                ps.translate(0F, 17F * (0.7F + 0.3F * k), 0F);
                ps.scale(1.4F * k, 7.5F * k, 1.4F * k);
                ps.translate(-0.5F, -0.5F, -0.5F);
                blocks.renderSingleBlock(i % 2 == 0 ? obsidian : crying, ps, buf, FULL, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            ps.popPose();
            // aura a sus espaldas (billboard: solo traslación en este marco)
            float yr = yaw * Mth.DEG_TO_RAD;
            ps.pushPose();
            ps.translate(Mth.sin(yr) * 10F, 1.5F, -Mth.cos(yr) * 10F);
            ps.mulPose(camera);
            float sz = 30F * k * (0.9F + 0.1F * Mth.sin(t * 0.07F));
            float c = 0.45F * k;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.FLARE)), -sz, -sz, 0, sz, -sz, 0, sz, sz, 0, -sz, sz, 0,
                    0, 1, c * (frenzy ? 1F : 0.7F), c * 0.15F, c);
            ps.popPose();
        }

        // ---- cortina de tentáculos colgantes
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int i = 0; i < 12; i++) {
                float a = Mth.TWO_PI * i / 12 + 0.2F;
                final float ph = i * 1.31F;
                final float agit = frenzy ? 1.8F : 1F;
                ps.pushPose();
                ps.translate(Mth.cos(a) * 5.4F, -7.0F, Mth.sin(a) * 5.4F);
                ps.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(a)));
                ps.mulPose(Axis.ZP.rotationDegrees(-172F));
                TentacleMesh.render(ps, vc, FULL, overlay, (18F + (i % 3) * 3F) * (0.4F + 0.6F * k), 0.95F, 18, (j, along) -> new float[]{
                        0.07F * Mth.sin(t * 0.05F * agit + ph + j * 0.4F) * agit,
                        0.05F * Mth.cos(t * 0.04F * agit + ph + j * 0.35F) * agit - 0.01F},
                        pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.2F, pass == 0 ? 1F : 0.8F, 1F);
                ps.popPose();
            }
        }
    }

    /** En el marco del Ojo (pupila hacia +Z): remolino negro-púrpura mientras traga. */
    static void renderPupilVortex(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float age, float R) {
        if (e.getState() != UnboundObserver.S_MAW) return;
        float open = VoidEyeRenderer.smooth(age / UnboundObserver.MAW_OPEN)
                * (1F - VoidEyeRenderer.smooth((age - (UnboundObserver.MAW_TICKS - 10)) / 4F));
        if (open <= 0.01F) return;
        ps.pushPose();
        ps.translate(0F, 0F, R * 1.06F);
        for (int layer = 0; layer < 2; layer++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(t * (layer == 0 ? 9F : -14F)));
            float sz = R * (layer == 0 ? 1.7F : 1.1F) * open;
            float c = layer == 0 ? 0.65F : 0.9F;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.VORTEX)), -sz, -sz, layer * 0.02F, sz, -sz, layer * 0.02F,
                    sz, sz, layer * 0.02F, -sz, sz, layer * 0.02F, 0, 1, c * 0.6F, c * 0.12F, c);
            ps.popPose();
        }
        ps.popPose();
    }
}

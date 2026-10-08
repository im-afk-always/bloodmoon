package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.VoidPalm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * La Palma del Vacío: una mano de energía oscura de ~100 bloques, palma abajo, con un ojo abierto en el centro de
 * la palma. Desciende sobre dos sellos en el piso (el borde de la onda y la zona letal); se pone incandescente al
 * acercarse, se deshace en una columna de luz al tocar el suelo y, tras el estallido, un muro de luz barre la superficie.
 */
public class VoidPalmRenderer extends EntityRenderer<VoidPalm> {
    private static final ResourceLocation ENERGY = VoidEyeRenderer.tex("energy"), ENERGY_GLOW = VoidEyeRenderer.tex("energy_glow"),
            SIGIL = VoidEyeRenderer.tex("sigil");
    private static final int FULL = VoidEyeRenderer.FULL;

    public VoidPalmRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VoidPalm entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VoidPalm entity) {
        return ENERGY;
    }

    @Override
    public void render(VoidPalm e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        Vec3 origin = e.getPosition(pt);
        Vec3 g = e.ground().subtract(origin);
        float h = VoidPalm.height(a);
        // la entidad se interpola; para que la palma no tiemble se dibuja desde el suelo
        Vec3 palm = g.add(0, h + 6, 0);

        // ---- sellos en el piso
        if (a < VoidPalm.BLAST) {
            float urgency = VoidEyeRenderer.smooth(a / VoidPalm.DESCEND);
            float pulse = 0.55F + 0.45F * Mth.sin(a * (0.08F + 0.6F * urgency));
            ps.pushPose();
            ps.translate(g.x, g.y + 0.15, g.z);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(a * 0.3F));
            float s = VoidPalm.BLAST_R, c = 0.3F * pulse;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -s, 0, -s, s, 0, -s, s, 0, s, -s, 0, s, 0, 1, c * 0.8F, c * 0.1F, c);
            ps.popPose();
            ps.mulPose(Axis.YP.rotationDegrees(-a * 0.8F));
            s = VoidPalm.LETHAL_R;
            c = (0.35F + 0.65F * urgency) * pulse;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -s, 0.05F, -s, s, 0.05F, -s, s, 0.05F, s, -s, 0.05F, s, 0, 1,
                    c, c * 0.12F, c * 0.6F);
            ps.popPose();
        }

        // ---- columna de luz que brota al tocar el suelo
        float tw = a - VoidPalm.DESCEND;
        float F = VoidPalm.FLARE;
        if (tw >= 0 && a < VoidPalm.BLAST) {
            float col = VoidEyeRenderer.smooth(tw / F);
            float wd = 4F + 40F * col;
            float c = 0.4F + 0.6F * col;
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            for (int i = 0; i < 3; i++) {
                ps.mulPose(Axis.YP.rotationDegrees(60F));
                VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(ENERGY_GLOW)), -wd, 0, 0, wd, 0, 0, wd * 0.4F, 260, 0, -wd * 0.4F, 260, 0,
                        0, 1, c, c * 0.92F, c);
            }
            ps.popPose();
        }

        // ---- onda de choque tras el estallido: un muro de luz que barre la superficie hasta BLAST_R
        if (a >= VoidPalm.BLAST) {
            float tb = a - VoidPalm.BLAST;
            float R = Math.min(VoidPalm.BLAST_R, 4F + 6F * tb);
            float fade = 1F - VoidEyeRenderer.smooth((tb - 22F) / (VoidPalm.END - VoidPalm.BLAST - 22F));
            if (fade > 0.01F) {
                float wallH = 18F * (1F - 0.6F * R / VoidPalm.BLAST_R) + 4F;
                float band = 10F + 6F * R / VoidPalm.BLAST_R;
                ps.pushPose();
                ps.translate(g.x, g.y + 0.2, g.z);
                int seg = 96;
                for (int i = 0; i < seg; i++) {
                    float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    float u0 = i / 8F, u1 = (i + 1) / 8F;
                    float c = fade;
                    // muro
                    VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(ENERGY_GLOW)), c0 * R, 0, s0 * R, c1 * R, 0, s1 * R,
                            c1 * R * 1.03F, wallH, s1 * R * 1.03F, c0 * R * 1.03F, wallH, s0 * R * 1.03F, u0, u1, c, c * 0.75F, c);
                    // anillo en el piso detrás del frente
                    float ri = Math.max(0F, R - band);
                    VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(ENERGY_GLOW)), c0 * ri, 0.1F, s0 * ri, c1 * ri, 0.1F, s1 * ri,
                            c1 * R, 0.1F, s1 * R, c0 * R, 0.1F, s0 * R, u0, u1, c * 0.8F, c * 0.25F, c);
                }
                ps.popPose();
            }
        }

        // ---- la mano: se pone incandescente al acercarse al suelo y se deshace en la luz del impacto
        float squash = a < VoidPalm.DESCEND ? 1F : 1F - VoidEyeRenderer.smooth(tw / F);
        float white = a < VoidPalm.DESCEND
                ? 0.45F * VoidEyeRenderer.smooth((a - VoidPalm.DESCEND + VoidPalm.DARKEN) / VoidPalm.DARKEN)
                : 1F;
        if (squash <= 0.01F) {
            super.render(e, entityYaw, pt, ps, buf, packedLight);
            return;
        }
        float appear = VoidEyeRenderer.smooth(a / 40F);
        float sc = appear * squash;
        ps.pushPose();
        ps.translate(palm.x, palm.y, palm.z);
        ps.mulPose(Axis.YP.rotationDegrees(a * 0.05F));
        ps.scale(sc, sc, sc);
        for (int pass = 0; pass < 2; pass++) {
            RenderType type = pass == 0 ? RenderType.entityCutoutNoCull(ENERGY) : RenderType.eyes(ENERGY_GLOW);
            float gl = 0.8F + 0.2F * Mth.sin(a * 0.15F);
            float r = pass == 0 ? 1F : Mth.lerp(white, 0.75F * gl, 1F), gg = pass == 0 ? 1F : Mth.lerp(white, 0.2F * gl, 0.95F),
                    b = pass == 0 ? 1F : Mth.lerp(white, gl, 1F);
            float grow = pass == 0 ? 1F : 1.06F + 0.12F * white;
            // palma
            VoidFistRenderer.ellipsoid(ps, buf.getBuffer(type), 0F, 0F, 0F, 24F * grow, 6F * grow, 28F * grow, r, gg, b);
            // dedos (hacia +Z), curvados hacia abajo; pulgar al costado
            float[][] fingers = {{-15F, 40F, 0.92F}, {-5F, 46F, 1F}, {5F, 44F, 1F}, {15F, 36F, 0.88F}};
            for (int f = 0; f < 4; f++) {
                float fx = fingers[f][0], len = fingers[f][1], wdt = fingers[f][2];
                float curl = 0.35F + 0.08F * Mth.sin(a * 0.05F + f);
                Vec3[] pts = new Vec3[12];
                float[] rad = new float[12];
                Vec3 p = new Vec3(fx, 0, 22);
                double ang = 0;
                for (int i = 0; i < 12; i++) {
                    pts[i] = p;
                    rad[i] = (5.2F - 2.6F * i / 11F) * wdt;
                    ang += curl / 11 * (i > 3 ? 1.6 : 0.4);
                    p = p.add(fx * 0.012, -Math.sin(ang) * len / 11, Math.cos(ang) * len / 11);
                }
                VoidFistRenderer.tube(ps.last(), buf.getBuffer(type), pts, rad, grow, -a * 0.03F, r, gg, b);
                VoidFistRenderer.ellipsoid(ps, buf.getBuffer(type), (float) p.x, (float) p.y, (float) p.z, 2.4F * grow, 3.2F * grow, 2.4F * grow, r, gg, b);
            }
            Vec3[] thumb = new Vec3[8];
            float[] trad = new float[8];
            for (int i = 0; i < 8; i++) {
                double s = i / 7.0;
                thumb[i] = new Vec3(22 + 18 * s, -6 * s * s, 4 + 14 * s);
                trad[i] = 5.5F - 3F * (float) s;
            }
            VoidFistRenderer.tube(ps.last(), buf.getBuffer(type), thumb, trad, grow, -a * 0.03F, r, gg, b);
            // muñeca que se pierde hacia arriba
            Vec3[] wrist = {new Vec3(0, 2, -22), new Vec3(0, 14, -32), new Vec3(0, 34, -38), new Vec3(0, 60, -40)};
            VoidFistRenderer.tube(ps.last(), buf.getBuffer(type), wrist, new float[]{18F, 15F, 12F, 4F}, grow, -a * 0.02F, r, gg, b);
        }
        // un ojo abierto en el centro de la palma, mirando hacia abajo
        ps.pushPose();
        ps.translate(0F, -5.2F, 2F);
        ps.mulPose(Axis.XP.rotationDegrees(90F));
        float R = 7F;
        PoseStack.Pose pose = ps.last();
        int ov = OverlayTexture.NO_OVERLAY;
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.BALL)), R, FULL, ov, 1F, 1F, 1F);
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.BALL_GLOW)), R * 1.002F, FULL, ov, 1F, 0.35F, 1F);
        VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), R * 1.004F, 0.06F, 0.4F, 0F, 1F, FULL, ov, 1F, 1F, 1F);
        ps.popPose();
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }
}

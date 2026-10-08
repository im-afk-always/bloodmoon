package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.ColossalEye;
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
import net.minecraft.world.phys.Vec3;

/**
 * Ojo Colosal: un ojo de 48 bloques que se abre en el cielo mirando hacia abajo, rodeado por un anillo de runas.
 * En el piso, un sello que late cada vez más rápido marca dónde caerá el rayo; el rayo es una columna
 * de luz de 20 bloques de radio con un núcleo blanco.
 */
public class ColossalEyeRenderer extends EntityRenderer<ColossalEye> {
    private static final ResourceLocation SIGIL = VoidEyeRenderer.tex("sigil");
    private static final int FULL = VoidEyeRenderer.FULL;

    public ColossalEyeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(ColossalEye entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ColossalEye entity) {
        return VoidEyeRenderer.BALL;
    }

    private static float smooth(float x) {
        return VoidEyeRenderer.smooth(x);
    }

    @Override
    public void render(ColossalEye e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        float R = ColossalEye.EYE_R;
        float close = 1F - smooth((a - (ColossalEye.END - 38)) / 30F);
        float open = smooth(a / ColossalEye.OPEN) * close;
        if (a >= ColossalEye.LOCK && a < ColossalEye.FIRE) open *= 0.72F;               // entrecierra: apunta
        if (a >= ColossalEye.FIRE && a < ColossalEye.FIRE + ColossalEye.FIRE_LEN) open *= 1.15F;
        float glowK = (0.35F + 0.65F * smooth((a - 20) / 60F)) * close;
        if (a >= ColossalEye.LOCK) glowK = Math.min(1F, glowK + 0.3F);
        int overlay = OverlayTexture.NO_OVERLAY;

        // ---------------- el ojo, mirando hacia abajo
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(a * 0.02F) * 8F));
        ps.mulPose(Axis.XP.rotationDegrees(90F + Mth.sin(a * 0.031F) * 6F));
        PoseStack.Pose pose = ps.last();
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.BALL)), R, FULL, overlay, 1F, 1F, 1F);
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.BALL_GLOW)), R * 1.001F, FULL, overlay,
                glowK, glowK * 0.35F, glowK);
        float pw, ph;
        if (a < ColossalEye.LOCK) { pw = 0.08F; ph = 0.38F; }
        else if (a < ColossalEye.FIRE) { pw = Mth.lerp(smooth((a - ColossalEye.LOCK) / 25F), 0.08F, 0.025F); ph = 0.42F; }
        else { pw = 0.32F; ph = 0.34F; }
        VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), R * 1.004F, pw, ph, 0F, 1F, FULL, overlay, 1F, 1F, 1F);
        if (a >= ColossalEye.LOCK) {
            float k = Math.min(1F, (a - ColossalEye.LOCK) / 30F) * close;
            VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE)), R * 1.005F, pw, ph, 1F, 1.8F, FULL, overlay, k, 0.6F * k, k);
        }
        VertexConsumer lid = buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.LID));
        VoidEyeRenderer.lid(ps, lid, R, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 62F), true, overlay);
        VoidEyeRenderer.lid(ps, lid, R * 1.006F, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 48F), false, overlay);
        ps.popPose();

        // ---------------- anillo de runas alrededor
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(a * 0.6F));
        float rk = smooth(a / 50F) * close;
        VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES)), R + 10F, 4F, 96, 16, 0.8F * rk, 0.25F * rk, rk, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(70F));
        VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.RUNES)), R + 15F, 3F, 96, 16, 0.6F * rk, 0.2F * rk, 0.9F * rk, 0F);
        ps.popPose();

        // ---------------- sello en el piso y rayo
        Vec3 g = e.groundPoint().subtract(e.getPosition(pt));
        float fireIn = smooth((a - ColossalEye.FIRE) / 3F) * (1F - smooth((a - (ColossalEye.FIRE + ColossalEye.FIRE_LEN)) / 12F));
        if (a < ColossalEye.FIRE + 4) {
            float urgency = smooth(a / ColossalEye.FIRE);
            float pulse = 0.55F + 0.45F * Mth.sin(a * (0.15F + 0.9F * urgency));
            float c = pulse * (0.35F + 0.65F * urgency);
            float size = ColossalEye.BEAM_R;
            ps.pushPose();
            ps.translate(g.x, g.y + 0.12, g.z);
            ps.mulPose(Axis.YP.rotationDegrees(-a * 1.5F));
            boolean locked = a >= ColossalEye.LOCK;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1,
                    c, c * (locked ? 0.5F : 0.12F), c * (locked ? 0.9F : 0.6F));
            ps.popPose();
            if (locked) {   // hilo de luz que anuncia el disparo
                float k = smooth((a - ColossalEye.LOCK) / 30F) * (0.6F + 0.4F * Mth.sin(a * 2.3F));
                VoidEyeRenderer.band(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE)), 0.8F, (float) (-R - g.y), 12, 1,
                        0.8F * k, 0.3F * k, k, (float) g.y);
            }
        }
        if (fireIn > 0.01F) {
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE));
            float y0 = (float) g.y - 70F, h = -R - y0;
            float f = fireIn * (0.9F + 0.1F * Mth.sin(a * 1.9F));
            VoidEyeRenderer.band(ps.last(), vc, ColossalEye.BEAM_R, h, 64, 1, 0.2F * f, 0.04F * f, 0.32F * f, y0);
            VoidEyeRenderer.band(ps.last(), vc, ColossalEye.BEAM_R * 0.68F, h, 64, 1, 0.38F * f, 0.1F * f, 0.55F * f, y0);
            VoidEyeRenderer.band(ps.last(), vc, ColossalEye.BEAM_R * 0.38F, h, 48, 1, 0.75F * f, 0.35F * f, 0.95F * f, y0);
            VoidEyeRenderer.band(ps.last(), vc, ColossalEye.BEAM_R * 0.14F, h, 32, 1, f, 0.95F * f, f, y0);
            // destello en el impacto
            ps.pushPose();
            ps.translate(g.x, g.y + 1, g.z);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            float s = ColossalEye.BEAM_R * 2.6F * f;
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.FLARE)), -s, -s, 0, s, -s, 0, s, s, 0, -s, s, 0, 0, 1,
                    f, 0.8F * f, f);
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }
}

package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.EyeTentacle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Aviso en el piso (círculo de runas que late) y luego el tentáculo que brota, ondula y azota. */
public class EyeTentacleRenderer extends EntityRenderer<EyeTentacle> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/tentacle.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/tentacle_glow.png");
    private static final ResourceLocation SIGIL = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/sigil.png");

    public EyeTentacleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(EyeTentacle entity) {
        return TEX;
    }

    @Override
    public void render(EyeTentacle e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float age = e.tickCount + pt;
        // círculo de aviso
        if (age < EyeTentacle.EMERGE + 6) {
            float k = Mth.clamp(age / EyeTentacle.EMERGE, 0F, 1F);
            float fade = age > EyeTentacle.EMERGE ? 1F - (age - EyeTentacle.EMERGE) / 6F : 1F;
            float pulse = 0.6F + 0.4F * Mth.sin(age * (0.4F + 0.8F * k));
            float size = 2.6F * (0.7F + 0.3F * k);
            float c = pulse * fade * (0.4F + 0.6F * k);
            ps.pushPose();
            ps.translate(0F, 0.03F, 0F);
            ps.mulPose(Axis.YP.rotationDegrees(age * 5F));
            PoseStack.Pose pose = ps.last();
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(SIGIL));
            v(vc, pose, -size, -size, 0, 0, c);
            v(vc, pose, -size, size, 0, 1, c);
            v(vc, pose, size, size, 1, 1, c);
            v(vc, pose, size, -size, 1, 0, c);
            v(vc, pose, size, -size, 1, 0, c);
            v(vc, pose, size, size, 1, 1, c);
            v(vc, pose, -size, size, 0, 1, c);
            v(vc, pose, -size, -size, 0, 0, c);
            ps.popPose();
        }
        float ext = e.extension(pt);
        if (ext <= 0.01F) return;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0 || e.deathTime > 0));
        float slam = 0F;
        int start = e.slamStart();
        if (start >= 0) {
            float t = e.tickCount - start + pt;
            if (t < EyeTentacle.SLAM_WINDUP) slam = -0.6F * smoothstep(t / EyeTentacle.SLAM_WINDUP);
            else if (t < EyeTentacle.SLAM_WINDUP + 3) slam = Mth.lerp((t - EyeTentacle.SLAM_WINDUP) / 3F, -0.6F, 1.55F);
            else slam = 1.55F * (1F - smoothstep((t - EyeTentacle.SLAM_WINDUP - 3) / 7F));
        }
        final float slamF = slam;
        int light = Math.max(packedLight, LightTexture.pack(10, 10));
        ps.pushPose();
        if (start >= 0) ps.mulPose(Axis.YP.rotationDegrees(-e.slamYaw() + 90F));   // la flexión en X apunta hacia el objetivo
        float wave = e.tickCount * 0.12F + pt * 0.12F;
        float len = EyeTentacle.LENGTH * ext;
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(TEX) : RenderType.eyes(GLOW));
            TentacleMesh.render(ps, vc, pass == 0 ? light : LightTexture.FULL_BRIGHT, overlay, len, 0.75F, 14, (i, along) -> new float[]{
                    0.09F * Mth.sin(wave + i * 0.55F) * (1F - Math.abs(slamF)) + slamF * 0.13F,
                    0.08F * Mth.cos(wave * 0.8F + i * 0.47F) * (1F - Math.abs(slamF))},
                    pass == 0 ? 1F : 0.6F, pass == 0 ? 1F : 0.2F, pass == 0 ? 1F : 0.9F, 1F);
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    private static float smoothstep(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float z, float u, float v, float c) {
        vc.addVertex(pose, x, 0F, z).setColor(c * 0.8F, c * 0.25F, c, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0F, 1F, 0F);
    }
}

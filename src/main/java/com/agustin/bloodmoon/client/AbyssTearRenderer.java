package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.AbyssTear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Lágrima del Cielo: un orbe de luz (núcleo blanco, halo violeta y estela) que cae despacio sobre un sello en el piso.
 * Al tocarlo se apaga un instante; luego una luz blanca crece, colapsa de golpe en un punto y estalla.
 */
public class AbyssTearRenderer extends EntityRenderer<AbyssTear> {
    private static final ResourceLocation SIGIL = VoidEyeRenderer.tex("sigil");

    public AbyssTearRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(AbyssTear entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssTear entity) {
        return VoidEyeRenderer.FLARE;
    }

    private void billboard(PoseStack ps, MultiBufferSource buf, RenderType type, float s, float spin, float r, float g, float b, float z) {
        ps.pushPose();
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(type), -s, -s, z, s, -s, z, s, s, z, -s, s, z, 0, 1, r, g, b);
        ps.popPose();
    }

    @Override
    public void render(AbyssTear e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        int f = e.flight();
        float since = a - f;
        Vec3 g = e.target().subtract(e.getPosition(pt));

        if (since < 0) {
            // en vuelo: un orbe de luz con estela
            float pulse = 1F + 0.12F * Mth.sin(a * 0.7F);
            Vec3 dir = g.lengthSqr() > 1e-4 ? g.normalize() : new Vec3(0, -1, 0);
            for (int i = 5; i >= 1; i--) {      // estela: orbes cada vez más chicos y tenues hacia atrás
                float t = i / 5F;
                float c = 0.55F * (1F - t);
                ps.pushPose();
                ps.translate(-dir.x * i * 2.2, -dir.y * i * 2.2, -dir.z * i * 2.2);
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), (4.2F - 2.8F * t) * pulse, a * 4F + i * 40, 0.8F * c, 0.35F * c, c, 0F);
                ps.popPose();
            }
            billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), 6.5F * pulse, a * 3F, 0.75F, 0.3F, 1F, 0F);      // halo violeta
            billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), 3.6F * pulse, -a * 5F, 1F, 0.75F, 1F, 0.005F);  // cuerpo
            billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), 1.8F, a * 7F, 1F, 1F, 1F, 0.01F);              // núcleo blanco
        } else if (since < AbyssTear.BOOM) {
            ps.pushPose();
            ps.translate(g.x, g.y + 1.2, g.z);
            if (since < 3) {
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), 1.6F, 0F, 0.45F, 0.2F, 0.6F, 0F);   // reposa, casi apagado
            } else if (since < AbyssTear.LIGHT) {
                float k = VoidEyeRenderer.smooth((since - 3) / (AbyssTear.LIGHT - 3F));
                float s = 1.5F + 15F * k;
                float c = 0.3F + 0.7F * k;
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), s, a * 2F, c, 0.8F * c, c, 0F);
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), s * 0.45F, -a * 4F, 1F, 1F, 1F, 0.005F);
            } else {
                float k = (since - AbyssTear.LIGHT) / AbyssTear.COLLAPSE;
                float s = Mth.lerp(k * k, 16.5F, 0.4F);
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), s, a * 6F, 1F, 1F, 1F, 0F);
                billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), s * 0.6F, -a * 9F, 1F, 1F, 1F, 0.005F);
            }
            ps.popPose();
        }

        // sello en el piso
        if (since < AbyssTear.BOOM) {
            float k = Mth.clamp(a / f, 0F, 1F);
            float urgency = since > 0 ? 1F : k;
            float size = AbyssTear.RADIUS * (since > 0 ? 1F : 1.35F - 0.35F * k);
            float col = (0.35F + 0.65F * urgency) * (0.7F + 0.3F * Mth.sin(a * (0.3F + 1.2F * urgency)));
            ps.pushPose();
            ps.translate(g.x, g.y + 0.1, g.z);
            ps.mulPose(Axis.YP.rotationDegrees(a * 3F));
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1,
                    col, col * (since > 0 ? 0.6F : 0.12F), col * (since > 0 ? 0.95F : 0.6F));
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }
}

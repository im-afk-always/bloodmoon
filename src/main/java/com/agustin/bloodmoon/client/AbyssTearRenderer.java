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
 * Lágrima del Cielo: un meteoro negro grande con halo púrpura que cae despacio sobre un sello en el piso.
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
            // en vuelo: núcleo negro con halo
            float s = 5.5F + 0.6F * Mth.sin(a * 0.6F);
            billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.FLARE), s, a * 3F, 0.75F, 0.15F, 0.95F, 0F);
            billboard(ps, buf, RenderType.eyes(VoidEyeRenderer.VORTEX), s * 0.8F, -a * 6F, 0.5F, 0.1F, 0.7F, 0.005F);
            billboard(ps, buf, RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL), 2.4F, 0F, 1F, 1F, 1F, 0.01F);
        } else if (since < AbyssTear.BOOM) {
            ps.pushPose();
            ps.translate(g.x, g.y + 1.2, g.z);
            if (since < 3) {
                billboard(ps, buf, RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL), 2.4F, 0F, 1F, 1F, 1F, 0F);   // reposa, apagado
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

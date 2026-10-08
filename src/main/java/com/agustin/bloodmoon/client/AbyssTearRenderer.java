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

/** Lágrima del Cielo: núcleo negro con halo púrpura y un círculo en el piso que se cierra hasta el impacto. */
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

    @Override
    public void render(AbyssTear e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        float k = Mth.clamp(a / e.flight(), 0F, 1F);
        // halo
        ps.pushPose();
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float s = 2.6F + 0.4F * Mth.sin(a * 0.8F);
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.FLARE)), -s, -s, 0, s, -s, 0, s, s, 0, -s, s, 0, 0, 1,
                0.7F, 0.15F, 0.9F);
        float c = 1.1F;
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), -c, -c, 0.01F, c, -c, 0.01F, c, c, 0.01F,
                -c, c, 0.01F, 0, 1, 1F, 1F, 1F);
        ps.popPose();
        // círculo en el piso
        Vec3 g = e.target().subtract(e.getPosition(pt));
        float size = AbyssTear.RADIUS * (1.6F - 0.6F * k);
        float col = (0.4F + 0.6F * k) * (0.7F + 0.3F * Mth.sin(a * (0.3F + k)));
        ps.pushPose();
        ps.translate(g.x, g.y + 0.1, g.z);
        ps.mulPose(Axis.YP.rotationDegrees(a * 3F));
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1,
                col, col * 0.12F, col * 0.6F);
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }
}

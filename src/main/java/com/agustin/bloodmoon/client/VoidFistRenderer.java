package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.VoidFist;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Puño del Vacío: una grieta en el aire junto al Ojo, un brazo de energía oscura (núcleo negro-púrpura con
 * vetas que fluyen y un halo púrpura) que se arquea hasta un puño suspendido sobre el objetivo, y el golpe.
 */
public class VoidFistRenderer extends EntityRenderer<VoidFist> {
    private static final ResourceLocation ENERGY = VoidEyeRenderer.tex("energy"), ENERGY_GLOW = VoidEyeRenderer.tex("energy_glow"),
            SIGIL = VoidEyeRenderer.tex("sigil");
    private static final int FULL = VoidEyeRenderer.FULL;

    public VoidFistRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VoidFist entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VoidFist entity) {
        return ENERGY;
    }

    private static float smooth(float x) {
        return VoidEyeRenderer.smooth(x);
    }

    @Override
    public void render(VoidFist e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float a = e.tickCount + pt;
        Vec3 origin = e.getPosition(pt);
        Vec3 A = e.anchor().subtract(origin);
        Vec3 ground = new Vec3(0, 2.6, 0);
        Vec3 hover = new Vec3(0, VoidFist.HOVER, 0);
        float fade = 1F - smooth((a - VoidFist.HOLD) / (VoidFist.END - VoidFist.HOLD));
        float open = smooth(a / VoidFist.OPEN) * fade;

        // puño: sale de la grieta, queda suspendido, cae
        Vec3 fist;
        if (a < VoidFist.OPEN) fist = A;
        else if (a < VoidFist.REACH) fist = A.lerp(hover, smooth((a - VoidFist.OPEN) / (VoidFist.REACH - VoidFist.OPEN)));
        else if (a < VoidFist.LOCK) fist = hover.add(0, 0.6 * Math.sin(a * 0.5), 0);
        else if (a < VoidFist.SLAM) {
            float k = (a - VoidFist.LOCK) / (VoidFist.SLAM - VoidFist.LOCK);
            fist = hover.lerp(ground, k * k);
        } else fist = ground;

        // grieta en el aire
        ps.pushPose();
        ps.translate(A.x, A.y, A.z);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees(a * 4F));
        float rw = 3.2F * open, rh = 5.5F * open;
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.VORTEX)), -rw, -rh, 0, rw, -rh, 0, rw, rh, 0, -rw, rh, 0, 0, 1,
                0.7F * open, 0.15F * open, open);
        float cw = 1.4F * open, ch = 3.2F * open;
        VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), -cw, -ch, 0.01F, cw, -ch, 0.01F, cw, ch, 0.01F,
                -cw, ch, 0.01F, 0, 1, 1F, 1F, 1F);
        ps.popPose();

        // brazo arqueado
        if (a >= VoidFist.OPEN * 0.6F && fade > 0.02F) {
            Vec3 ctrl = A.add(fist).scale(0.5).add(0, 10 + 0.15 * A.subtract(fist).length(), 0);
            int n = 28;
            Vec3[] pts = new Vec3[n + 1];
            float[] rad = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n;
                pts[i] = A.scale((1 - s) * (1 - s)).add(ctrl.scale(2 * (1 - s) * s)).add(fist.scale(s * s));
                rad[i] = (float) ((2.3 - 0.7 * s) * fade) * (1F + 0.06F * Mth.sin((float) (a * 0.6 - i * 0.7)));
            }
            float flow = -a * 0.08F;
            tube(ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(ENERGY)), pts, rad, 1F, flow, 1F, 1F, 1F);
            float g = 0.75F + 0.25F * Mth.sin(a * 0.4F);
            tube(ps.last(), buf.getBuffer(RenderType.eyes(ENERGY_GLOW)), pts, rad, 1.32F, flow * 1.6F, 0.75F * g * fade, 0.2F * g * fade, g * fade);
            // el puño, orientado según el último tramo
            Vec3 dir = fist.subtract(pts[n - 2]).normalize();
            renderFist(ps, buf, fist, dir, a, fade);
        }

        // sello en el piso
        if (a < VoidFist.SLAM + 2) {
            float urgency = smooth(a / VoidFist.SLAM);
            float pulse = 0.55F + 0.45F * Mth.sin(a * (0.25F + 1.1F * urgency));
            float c = pulse * (0.3F + 0.7F * urgency);
            boolean locked = a >= VoidFist.LOCK;
            float size = VoidFist.RADIUS;
            ps.pushPose();
            ps.translate(0F, 0.1F, 0F);
            ps.mulPose(Axis.YP.rotationDegrees(-a * 3F));
            VoidEyeRenderer.quad2(ps.last(), buf.getBuffer(RenderType.eyes(SIGIL)), -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1,
                    c, c * (locked ? 0.45F : 0.1F), c * (locked ? 0.95F : 0.6F));
            ps.popPose();
        }
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    /** Puño cerrado: palma, cuatro nudillos y pulgar; los nudillos miran en la dirección del golpe. */
    private static void renderFist(PoseStack ps, MultiBufferSource buf, Vec3 at, Vec3 dir, float a, float fade) {
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(new org.joml.Quaternionf().rotationTo(0F, -1F, 0F, (float) dir.x, (float) dir.y, (float) dir.z));
        float s = 1.25F * fade;
        ps.scale(s, s, s);
        for (int pass = 0; pass < 2; pass++) {
            RenderType type = pass == 0 ? RenderType.entityCutoutNoCull(ENERGY) : RenderType.eyes(ENERGY_GLOW);
            float g = pass == 0 ? 1F : 0.85F + 0.15F * Mth.sin(a * 0.5F);
            float grow = pass == 0 ? 1F : 1.18F;
            float r = pass == 0 ? 1F : 0.75F * g, gg = pass == 0 ? 1F : 0.2F * g, b = pass == 0 ? 1F : g;
            ellipsoid(ps, buf.getBuffer(type), 0F, 0.2F, 0F, 2.3F * grow, 1.9F * grow, 1.7F * grow, r, gg, b);
            for (int i = 0; i < 4; i++) {
                ellipsoid(ps, buf.getBuffer(type), -1.5F + i, -1.65F, 0.35F, 0.62F * grow, 0.7F * grow, 0.75F * grow, r, gg, b);
            }
            ellipsoid(ps, buf.getBuffer(type), 2.2F, -0.4F, 0.6F, 0.6F * grow, 1.2F * grow, 0.6F * grow, r, gg, b);
        }
        ps.popPose();
    }

    private static void ellipsoid(PoseStack ps, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, float r, float g, float b) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.scale(rx, ry, rz);
        PoseStack.Pose pose = ps.last();
        int lat = 10, lon = 14;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                ev(pose, vc, t0, p0, j / (float) lon, i / (float) lat, r, g, b);
                ev(pose, vc, t0, p1, (j + 1) / (float) lon, i / (float) lat, r, g, b);
                ev(pose, vc, t1, p1, (j + 1) / (float) lon, (i + 1) / (float) lat, r, g, b);
                ev(pose, vc, t1, p0, j / (float) lon, (i + 1) / (float) lat, r, g, b);
            }
        }
        ps.popPose();
    }

    private static void ev(PoseStack.Pose pose, VertexConsumer vc, float th, float ph, float u, float v, float r, float g, float b) {
        float x = Mth.sin(th) * Mth.cos(ph), y = Mth.cos(th), z = Mth.sin(th) * Mth.sin(ph);
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(pose, x, y, z);
    }

    /** Tubo a lo largo de una polilínea (marcos de transporte paralelo), con la textura fluyendo en v. */
    private static void tube(PoseStack.Pose pose, VertexConsumer vc, Vec3[] pts, float[] rad, float scale, float flow, float r, float g, float b) {
        int n = pts.length, sides = 10;
        Vector3f[][] ring = new Vector3f[n][sides + 1];
        Vector3f[][] nor = new Vector3f[n][sides + 1];
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 prevU = null;
        Matrix4f m = pose.pose();
        for (int i = 0; i < n; i++) {
            Vec3 t = pts[Math.min(i + 1, n - 1)].subtract(pts[Math.max(i - 1, 0)]).normalize();
            Vec3 u = prevU == null ? t.cross(Math.abs(t.y) > 0.9 ? new Vec3(1, 0, 0) : up).normalize()
                    : prevU.subtract(t.scale(prevU.dot(t))).normalize();
            Vec3 w = t.cross(u);
            prevU = u;
            for (int k = 0; k <= sides; k++) {
                double ang = Math.PI * 2 * k / sides;
                Vec3 d = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
                Vec3 p = pts[i].add(d.scale(rad[i] * scale));
                ring[i][k] = m.transformPosition((float) p.x, (float) p.y, (float) p.z, new Vector3f());
                nor[i][k] = pose.normal().transform(new Vector3f((float) d.x, (float) d.y, (float) d.z), new Vector3f()).normalize();
            }
        }
        for (int i = 0; i < n - 1; i++) {
            float v0 = i / 6F + flow, v1 = (i + 1) / 6F + flow;
            for (int k = 0; k < sides; k++) {
                float u0 = k / (float) sides, u1 = (k + 1) / (float) sides;
                put(vc, ring[i][k], nor[i][k], u0, v0, r, g, b);
                put(vc, ring[i][k + 1], nor[i][k + 1], u1, v0, r, g, b);
                put(vc, ring[i + 1][k + 1], nor[i + 1][k + 1], u1, v1, r, g, b);
                put(vc, ring[i + 1][k], nor[i + 1][k], u0, v1, r, g, b);
            }
        }
    }

    private static void put(VertexConsumer vc, Vector3f p, Vector3f n, float u, float v, float r, float g, float b) {
        vc.addVertex(p.x(), p.y(), p.z()).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(n.x(), n.y(), n.z());
    }
}

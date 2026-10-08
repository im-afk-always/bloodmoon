package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnboundObserver;
import com.agustin.bloodmoon.entity.VoidEye;
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
 * El Observador Desatado: seis patas-tentáculo, pelvis, abdomen y un pecho ancho de músculo desollado con venas
 * que brillan, dos brazos que terminan en manojos de tentáculos y, arriba, el Ojo encajado en una capucha de carne
 * con sus párpados. Emerge del piso, se inclina para golpear, alza los brazos para invocar al Ojo Colosal,
 * cae de rodillas exhausto y al morir se hunde mientras el ojo se agrieta.
 */
public class UnboundObserverRenderer extends EntityRenderer<UnboundObserver> {
    private static final ResourceLocation MUSCLE = VoidEyeRenderer.tex("muscle"), MUSCLE_GLOW = VoidEyeRenderer.tex("muscle_glow"),
            SIGIL = VoidEyeRenderer.tex("sigil");
    private static final int FULL = VoidEyeRenderer.FULL;

    public UnboundObserverRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 6F;
    }

    @Override
    public boolean shouldRender(UnboundObserver entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < 320 * 320;
    }

    @Override
    public ResourceLocation getTextureLocation(UnboundObserver entity) {
        return MUSCLE;
    }

    private static float smooth(float x) {
        return VoidEyeRenderer.smooth(x);
    }

    // ------------------------------------------------------------------ pose

    /** {alzado del brazo (0 colgando, 90 al frente, 180 arriba), apertura lateral, codo}. */
    private static float[] armPose(UnboundObserver e, int side, float age, float t, float walkPos, float walkSpd) {
        float raise = 16F + 6F * Mth.sin(t * 0.05F + side) + walkSpd * 28F * Mth.sin(walkPos * 0.55F + (side > 0 ? 0F : Mth.PI));
        float roll = 14F, elbow = 15F;
        switch (e.getState()) {
            case UnboundObserver.S_SLAM -> {
                if (side == e.slamArm()) {
                    int hit = UnboundObserver.SLAM_HIT;
                    if (age < hit - 3) {
                        float k = smooth(age / (hit - 4F));
                        raise = Mth.lerp(k, raise, 172F); elbow = Mth.lerp(k, elbow, 50F); roll = 8F;
                    } else if (age < hit) {
                        float k = (age - (hit - 3)) / 3F;
                        raise = Mth.lerp(k, 172F, 72F); elbow = Mth.lerp(k, 50F, 18F); roll = 8F;
                    } else {
                        float k = smooth((age - hit) / (float) (UnboundObserver.SLAM_TICKS - hit));
                        raise = Mth.lerp(k, 72F, raise); elbow = Mth.lerp(k, 18F, elbow);
                    }
                } else {
                    raise = 35F; roll = 28F;
                }
            }
            case UnboundObserver.S_COLOSSAL -> {
                float k = smooth(age / 14F);
                raise = Mth.lerp(k, raise, 168F + 4F * Mth.sin(t * 0.3F)); roll = Mth.lerp(k, roll, 22F); elbow = Mth.lerp(k, elbow, 8F);
            }
            case VoidEye.S_EXPOSED -> { raise = 64F; roll = 22F; elbow = 6F; }
            case VoidEye.S_SCREAM -> { raise = 45F; roll = 78F + 6F * Mth.sin(t * 0.8F); elbow = 14F; }
            case UnboundObserver.S_EMERGE -> { raise = 110F + 30F * Mth.sin(t * 0.07F + side); roll = 30F; elbow = 40F; }
            case VoidEye.S_GAZE_CHARGE, VoidEye.S_GAZE -> { raise = 8F; roll = 26F; elbow = 20F; }
            default -> {}
        }
        if (e.isDeadOrDying()) { raise = 20F + 10F * Mth.sin(t * 0.5F + side); roll = 40F; elbow = 60F; }
        return new float[]{raise, roll, elbow};
    }

    /** Inclinación del torso hacia adelante (grados). */
    private static float lean(UnboundObserver e, float age) {
        if (e.isDeadOrDying()) return 25F * smooth(e.deathTime / 60F);
        return switch (e.getState()) {
            case UnboundObserver.S_SLAM -> {
                int hit = UnboundObserver.SLAM_HIT;
                if (age < hit - 3) yield -10F * smooth(age / (hit - 4F));
                if (age < hit) yield Mth.lerp((age - (hit - 3)) / 3F, -10F, 35F);
                yield 35F * (1F - smooth((age - hit) / (float) (UnboundObserver.SLAM_TICKS - hit)));
            }
            case VoidEye.S_EXPOSED -> 22F * smooth(age / 12F);
            case UnboundObserver.S_COLOSSAL -> -14F * smooth(age / 14F);
            case VoidEye.S_SCREAM -> -18F;
            default -> 0F;
        };
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(UnboundObserver e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float t = e.tickCount + pt;
        float age = e.clientStateAge(pt);
        int state = e.getState();
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0));
        float walkPos = e.walkAnimation.position(pt), walkSpd = Math.min(1F, e.walkAnimation.speed(pt) * 4F);
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());

        float rise = state == UnboundObserver.S_EMERGE ? -36F * (1F - smooth((age - 10F) / 110F)) : 0F;
        float kneel = state == VoidEye.S_EXPOSED
                ? smooth(age / 12F) * (1F - smooth((age - (UnboundObserver.EXPOSED_TICKS - 15)) / 15F)) * UnboundObserver.KNEEL : 0F;
        float sink = e.isDeadOrDying() ? smooth(e.deathTime / (float) VoidEye.DEATH_TICKS) * 12F : 0F;
        float shake = state == VoidEye.S_SCREAM ? 0.3F : e.isDeadOrDying() ? 0.1F + 0.4F * e.deathTime / VoidEye.DEATH_TICKS : 0F;
        float glowK = state == VoidEye.S_EXPOSED ? 0.35F + 0.35F * Math.max(0F, Mth.sin(t * 0.31F))
                : e.getPhase() >= 5 ? 0.8F + 0.2F * Mth.sin(t * 0.2F) : 0.55F + 0.1F * Mth.sin(t * 0.07F);

        ps.pushPose();
        ps.translate(0F, rise - kneel - sink, 0F);
        if (shake > 0) ps.translate(Mth.sin(t * 2.3F) * shake, 0F, Mth.cos(t * 2.7F) * shake);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        renderLegs(ps, buf, t, walkPos, walkSpd, kneel > 0.1F, overlay);
        VertexConsumer muscle = buf.getBuffer(RenderType.entityCutoutNoCull(MUSCLE));

        // pelvis (fija) y torso (se inclina desde la cadera)
        ellipsoid(ps, muscle, 0, 12.6F, 0, 4.6F, 2.9F, 3.9F, overlay, 1F, 1F, 1F, 0F);
        ps.pushPose();
        ps.translate(0F, 12.5F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(lean(e, age)));
        ps.translate(0F, -12.5F, 0F);
        float breathe = 1F + 0.025F * Mth.sin(t * 0.08F);
        muscle = buf.getBuffer(RenderType.entityCutoutNoCull(MUSCLE));
        ellipsoid(ps, muscle, 0, 16.3F, 0.2F, 3.7F * breathe, 3.3F, 3.1F * breathe, overlay, 1F, 1F, 1F, 0.3F);
        ellipsoid(ps, muscle, 0, 20.6F, 0.4F, 7.3F * breathe, 4.3F, 5.1F * breathe, overlay, 1F, 1F, 1F, 0.6F);
        ellipsoid(ps, muscle, 7.7F, 22.1F, 0F, 3.4F, 3.0F, 3.2F, overlay, 1F, 1F, 1F, 0.1F);
        ellipsoid(ps, muscle, -7.7F, 22.1F, 0F, 3.4F, 3.0F, 3.2F, overlay, 1F, 1F, 1F, 0.8F);
        ellipsoid(ps, muscle, 0, 23.6F, 0.2F, 6.8F, 3.1F, 5.6F, overlay, 1F, 1F, 1F, 0.45F);
        ellipsoid(ps, muscle, 0, 26.4F, -2.6F, 6.4F, 6.4F, 4.6F, overlay, 0.85F, 0.85F, 0.85F, 0.2F);
        VertexConsumer glow = buf.getBuffer(RenderType.eyes(MUSCLE_GLOW));
        float gr = glowK * (e.getPhase() >= 5 ? 1F : 0.75F), gg = glowK * 0.2F, gb = glowK;
        ellipsoid(ps, glow, 0, 16.3F, 0.2F, 3.72F * breathe, 3.32F, 3.12F * breathe, overlay, gr, gg, gb, 0.3F);
        ellipsoid(ps, glow, 0, 20.6F, 0.4F, 7.32F * breathe, 4.32F, 5.12F * breathe, overlay, gr, gg, gb, 0.6F);
        ellipsoid(ps, glow, 0, 23.6F, 0.2F, 6.82F, 3.12F, 5.62F, overlay, gr, gg, gb, 0.45F);

        // brazos
        for (int side = -1; side <= 1; side += 2) renderArm(e, ps, buf, side, age, t, walkPos, walkSpd, overlay);
        // tentáculos de la espalda
        renderBackTentacles(ps, buf, t, e.isDeadOrDying() || state == VoidEye.S_SCREAM ? 2F : 1F, overlay);
        // el Ojo
        renderEye(e, ps, buf, pt, t, age, bodyYaw, overlay);
        ps.popPose();
        ps.popPose();

        // efectos en coordenadas del mundo
        Vec3 origin = e.getPosition(pt);
        if (state == UnboundObserver.S_SLAM && age < UnboundObserver.SLAM_HIT + 2) renderSlamWarning(e, ps, buf, t, age, origin);
        if (e.isBeamState() && !e.isDeadOrDying()) renderBeam(e, ps, buf, t, age, origin, rise - kneel - sink);
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    // ------------------------------------------------------------------ partes

    private static void renderLegs(PoseStack ps, MultiBufferSource buf, float t, float walkPos, float walkSpd, boolean spread, int overlay) {
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int i = 0; i < 6; i++) {
                float a = 30F + 60F * i;
                float ar = a * Mth.DEG_TO_RAD;
                final float phase = i * 1.05F;
                ps.pushPose();
                ps.translate(Mth.cos(ar) * 3.2F, 12.6F, Mth.sin(ar) * 3.2F);
                ps.mulPose(Axis.YP.rotationDegrees(-a));
                ps.mulPose(Axis.ZP.rotationDegrees(spread ? -150F : -164F));
                TentacleMesh.render(ps, vc, FULL, overlay, 17.5F, 1.55F, 16, (k, along) -> new float[]{
                        Mth.sin(walkPos * 0.55F + phase) * 0.07F * walkSpd + 0.02F * Mth.sin(t * 0.05F + phase + k * 0.4F),
                        0.07F + 0.015F * Mth.sin(t * 0.04F + phase)},
                        pass == 0 ? 1F : 0.5F, pass == 0 ? 1F : 0.15F, pass == 0 ? 1F : 0.7F, 1F);
                ps.popPose();
            }
        }
    }

    private static void renderArm(UnboundObserver e, PoseStack ps, MultiBufferSource buf, int side, float age, float t, float walkPos,
                                  float walkSpd, int overlay) {
        float[] pose = armPose(e, side, age, t, walkPos, walkSpd);
        ps.pushPose();
        ps.translate(side * 8.6F, 22.2F, 0F);
        ps.mulPose(Axis.ZP.rotationDegrees(side * pose[1]));
        ps.mulPose(Axis.XP.rotationDegrees(-pose[0]));
        VertexConsumer muscle = buf.getBuffer(RenderType.entityCutoutNoCull(MUSCLE));
        ellipsoid(ps, muscle, 0, -4.5F, 0, 2.1F, 5.0F, 2.1F, overlay, 1F, 1F, 1F, 0.2F);
        ps.translate(0F, -9F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(-pose[2]));
        muscle = buf.getBuffer(RenderType.entityCutoutNoCull(MUSCLE));
        ellipsoid(ps, muscle, 0, -4.4F, 0, 1.8F, 4.8F, 1.8F, overlay, 1F, 1F, 1F, 0.7F);
        ps.translate(0F, -8.6F, 0F);
        // mano: cinco tentáculos
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int f = 0; f < 5; f++) {
                final float ph = f * 1.3F + side;
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(180F + (f - 2) * 8F));
                ps.mulPose(Axis.ZP.rotationDegrees((f - 2) * 16F));
                TentacleMesh.render(ps, vc, FULL, overlay, 7F, 0.6F, 10, (k, along) -> new float[]{
                        0.12F * Mth.sin(t * 0.09F + ph + k * 0.6F), 0.1F * Mth.cos(t * 0.07F + ph + k * 0.5F)},
                        pass == 0 ? 1F : 0.5F, pass == 0 ? 1F : 0.15F, pass == 0 ? 1F : 0.7F, 1F);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void renderBackTentacles(PoseStack ps, MultiBufferSource buf, float t, float agitation, int overlay) {
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int i = 0; i < 5; i++) {
                final float ph = i * 1.9F;
                ps.pushPose();
                ps.translate((i - 2) * 2.4F, 23.5F, -3.6F);
                ps.mulPose(Axis.XP.rotationDegrees(-55F - Math.abs(i - 2) * 10F));
                ps.mulPose(Axis.ZP.rotationDegrees((i - 2) * 18F));
                TentacleMesh.render(ps, vc, FULL, overlay, 15F, 1.15F, 14, (k, along) -> new float[]{
                        (0.13F * Mth.sin(t * 0.05F * agitation + ph + k * 0.5F) + 0.04F) * agitation,
                        0.12F * Mth.cos(t * 0.04F * agitation + ph + k * 0.45F) * agitation},
                        pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.25F, pass == 0 ? 1F : 0.8F, 1F);
                ps.popPose();
            }
        }
    }

    private static void renderEye(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float pt, float t, float age, float bodyYaw,
                                  int overlay) {
        float R = UnboundObserver.EYE_R;
        ps.pushPose();
        ps.translate(0F, UnboundObserver.EYE_Y, 0.8F);
        float yaw = Mth.rotLerp(pt, e.yawO, e.yaw), pitch = Mth.lerp(pt, e.pitchO, e.pitch);
        float rel = Mth.clamp(Mth.wrapDegrees(yaw - bodyYaw), -75F, 75F);
        ps.mulPose(Axis.YP.rotationDegrees(-rel));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitch, -60F, 70F)));
        float[] gl = VoidEyeRenderer.glow(e, age, t);
        float[] pu = VoidEyeRenderer.pupil(e, age, t);
        PoseStack.Pose pose = ps.last();
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.BALL)), R, FULL, overlay, 1F, 1F, 1F);
        VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.BALL_GLOW)), R * 1.001F, FULL, overlay, gl[0], gl[1], gl[2]);
        VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), R * 1.004F, pu[0], pu[1], 0F, 1F, FULL, overlay, 1F, 1F, 1F);
        if (e.isBeamState()) {
            float k = e.isFiring() ? 1F : smooth(age / 20F);
            VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE)), R * 1.005F, pu[0], pu[1], 1F, 1.9F, FULL, overlay, k, 0.55F * k, k);
        }
        if (e.isDeadOrDying()) {
            float k = smooth(e.deathTime / (float) (VoidEye.DEATH_TICKS - 30));
            VoidEyeRenderer.sphere(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.CRACKS)), R * 1.006F, FULL, overlay, k, k * 0.9F, k);
        }
        float open = e.getState() == UnboundObserver.S_EMERGE ? smooth((age - 70F) / 40F) : VoidEyeRenderer.lidOpen(e, age, t);
        VertexConsumer lid = buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.LID));
        VoidEyeRenderer.lid(ps, lid, R, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 62F), true, overlay);
        VoidEyeRenderer.lid(ps, lid, R * 1.006F, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 48F), false, overlay);
        ps.popPose();
    }

    // ------------------------------------------------------------------ elipsoide

    /** Elipsoide con textura de músculo; uOff desplaza la textura para que las partes no se vean iguales. */
    static void ellipsoid(PoseStack ps, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, int overlay,
                          float r, float g, float b, float uOff) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.scale(rx, ry, rz);
        PoseStack.Pose pose = ps.last();
        int lat = 14, lon = 22;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                ev(pose, vc, t0, p0, j / (float) lon, i / (float) lat, overlay, r, g, b, uOff);
                ev(pose, vc, t0, p1, (j + 1) / (float) lon, i / (float) lat, overlay, r, g, b, uOff);
                ev(pose, vc, t1, p1, (j + 1) / (float) lon, (i + 1) / (float) lat, overlay, r, g, b, uOff);
                ev(pose, vc, t1, p0, j / (float) lon, (i + 1) / (float) lat, overlay, r, g, b, uOff);
            }
        }
        ps.popPose();
    }

    private static void ev(PoseStack.Pose pose, VertexConsumer vc, float th, float ph, float u, float v, int overlay, float r, float g, float b, float uOff) {
        float x = Mth.sin(th) * Mth.cos(ph), y = Mth.cos(th), z = Mth.sin(th) * Mth.sin(ph);
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 1F).setUv(u * 2F + uOff, v).setOverlay(overlay).setLight(FULL).setNormal(pose, x, y, z);
    }

    // ------------------------------------------------------------------ efectos

    private static void renderSlamWarning(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float age, Vec3 origin) {
        Vec3 p = e.getBeamEnd().subtract(origin);
        float k = Mth.clamp(age / UnboundObserver.SLAM_HIT, 0F, 1F);
        float pulse = 0.6F + 0.4F * Mth.sin(t * (0.5F + 1.2F * k));
        float size = 6.5F;
        float c = pulse * (0.4F + 0.6F * k);
        ps.pushPose();
        ps.translate(p.x, p.y + 0.08, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(t * 4F));
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(SIGIL));
        VoidEyeRenderer.quad2(ps.last(), vc, -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1, c, c * 0.15F, c * 0.55F);
        ps.popPose();
    }

    private static void renderBeam(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float t, float age, Vec3 origin, float yOff) {
        Vec3 center = new Vec3(0, UnboundObserver.EYE_Y + yOff, 0);
        Vec3 end = e.getBeamEnd().subtract(origin);
        Vec3 dir = end.subtract(center).normalize();
        Vec3 start = center.add(dir.scale(UnboundObserver.EYE_R * 1.01));
        Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(origin);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(VoidEyeRenderer.BEAM));
        PoseStack.Pose pose = ps.last();
        float scroll = -t * 0.35F;
        if (!e.isFiring()) {
            float flick = 0.5F + 0.5F * Mth.sin(t * 2.1F) * Mth.sin(t * 0.7F);
            float k = smooth(age / 10F) * (0.55F + 0.45F * flick);
            VoidEyeRenderer.ribbon(pose, vc, start, end, cam, 0.14F, scroll, 0.75F * k, 0.25F * k, k);
            return;
        }
        float in = smooth(age / 4F);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 1.7F);
        VoidEyeRenderer.ribbon(pose, vc, start, end, cam, 3.6F * in * pulse, scroll * 0.6F, 0.25F, 0.05F, 0.35F);
        VoidEyeRenderer.ribbon(pose, vc, start, end, cam, 1.6F * in * pulse, scroll, 0.9F, 0.25F, 1F);
        VoidEyeRenderer.ribbon(pose, vc, start, end, cam, 0.5F * in, scroll * 1.5F, 1F, 0.85F, 1F);
    }
}

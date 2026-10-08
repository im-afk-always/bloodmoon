package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.entity.UnboundObserver;
import com.agustin.bloodmoon.entity.VoidEye;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * El Observador Desatado, esculpido (malla .bmsh con color y brillo por vértice):
 * una falda de carne con dientes que se deshace en diez patas-tentáculo; un torso demacrado de columna en S y
 * omóplatos salientes; un costillar partido al medio que se abre como unas fauces (las costillas son los dientes)
 * y deja ver un núcleo que brilla; dos brazos de hueso y tendón con dedos larguísimos y dos bracitos atrofiados;
 * arriba, el Ojo en una cuenca de carne, coronado por cuernos y rodeado de ojos menores que parpadean; una barba de
 * tentáculos le cuelga bajo el Ojo y un halo roto de esquirlas de hueso gira detrás.
 */
public class UnboundObserverRenderer extends EntityRenderer<UnboundObserver> {
    private static final ResourceLocation SIGIL = VoidEyeRenderer.tex("sigil");
    private static final int FULL = VoidEyeRenderer.FULL;
    // posiciones del esqueleto (iguales a las del escultor)
    private static final float HIP_Y = 13F;
    private static final float[] SHOULDER = {6.4F, 23.4F, -0.6F}, SMALL_ARM = {3.4F, 18.8F, 1.2F}, EYE_C = {0F, 27.4F, 0.9F};

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
        return VoidEyeRenderer.WHITE;
    }

    private static float smooth(float x) {
        return VoidEyeRenderer.smooth(x);
    }

    // ------------------------------------------------------------------ pose

    /** {alzado del brazo (0 colgando, 90 al frente, 180 arriba), apertura lateral, codo}. */
    private static float[] armPose(UnboundObserver e, int side, float age, float t, float walkPos, float walkSpd) {
        float raise = 14F + 6F * Mth.sin(t * 0.05F + side) + walkSpd * 24F * Mth.sin(walkPos * 0.55F + (side > 0 ? 0F : Mth.PI));
        float roll = 12F + 3F * Mth.sin(t * 0.04F + side * 2), elbow = 18F + 6F * Mth.sin(t * 0.06F + side);
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
            case UnboundObserver.S_COLOSSAL, UnboundObserver.S_TEARS -> {
                float k = smooth(age / 14F);
                raise = Mth.lerp(k, raise, 168F + 5F * Mth.sin(t * 0.3F + side)); roll = Mth.lerp(k, roll, 24F); elbow = Mth.lerp(k, elbow, 10F);
            }
            case UnboundObserver.S_TITAN -> {      // hunde las garras en el piso
                float k = smooth(age / 10F);
                raise = Mth.lerp(k, raise, 58F); roll = Mth.lerp(k, roll, 30F); elbow = Mth.lerp(k, elbow, 2F);
            }
            case UnboundObserver.S_MAW -> {         // se abre el pecho con las manos
                float k = smooth(age / UnboundObserver.MAW_OPEN);
                raise = Mth.lerp(k, raise, 78F); roll = Mth.lerp(k, roll, 70F + 4F * Mth.sin(t * 0.5F)); elbow = Mth.lerp(k, elbow, 35F);
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
            case UnboundObserver.S_COLOSSAL, UnboundObserver.S_TEARS -> -16F * smooth(age / 14F);
            case UnboundObserver.S_TITAN -> 30F * smooth(age / 10F) * (1F - smooth((age - 40F) / 10F));
            case UnboundObserver.S_MAW -> -10F * smooth(age / 20F);
            case VoidEye.S_SCREAM -> -18F;
            default -> 1.5F * Mth.sin(age * 0.05F);
        };
    }

    /** Apertura de cada mitad del costillar (grados). */
    private static float ribOpen(UnboundObserver e, float age, float t) {
        if (e.isDeadOrDying()) return 35F + 10F * Mth.sin(t * 0.7F);
        return switch (e.getState()) {
            case UnboundObserver.S_MAW -> age < UnboundObserver.MAW_TICKS - 10
                    ? 72F * smooth(age / UnboundObserver.MAW_OPEN) + 4F * Mth.sin(t * 0.9F)
                    : 72F * (1F - smooth((age - (UnboundObserver.MAW_TICKS - 10)) / 3F));
            case VoidEye.S_SCREAM -> 30F + 6F * Mth.sin(t * 1.3F);
            case UnboundObserver.S_TEARS, UnboundObserver.S_COLOSSAL -> 16F;
            case VoidEye.S_EXPOSED -> 10F;
            default -> 3F + 3F * Mth.sin(t * 0.07F);
        };
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(UnboundObserver e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        BodyMesh.Part skirt = BodyMesh.get("skirt");
        float t = e.tickCount + pt;
        float age = e.clientStateAge(pt);
        int state = e.getState();
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0));
        int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), 11), LightTexture.sky(packedLight));
        float walkPos = e.walkAnimation.position(pt), walkSpd = Math.min(1F, e.walkAnimation.speed(pt) * 4F);
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());

        float rise = state == UnboundObserver.S_EMERGE ? -36F * (1F - smooth((age - 10F) / 110F)) : 0F;
        float kneel = state == VoidEye.S_EXPOSED
                ? smooth(age / 12F) * (1F - smooth((age - (UnboundObserver.EXPOSED_TICKS - 15)) / 15F)) * UnboundObserver.KNEEL : 0F;
        float sink = e.isDeadOrDying() ? smooth(e.deathTime / (float) VoidEye.DEATH_TICKS) * 12F : 0F;
        float shake = state == VoidEye.S_SCREAM ? 0.3F : e.isDeadOrDying() ? 0.1F + 0.4F * e.deathTime / VoidEye.DEATH_TICKS
                : state == UnboundObserver.S_MAW && age > UnboundObserver.MAW_OPEN ? 0.12F : 0F;
        float glowK = state == VoidEye.S_EXPOSED ? 0.4F + 0.5F * Math.max(0F, Mth.sin(t * 0.31F))
                : e.getPhase() >= 5 ? 1.0F + 0.25F * Mth.sin(t * 0.2F) : 0.75F + 0.15F * Mth.sin(t * 0.07F);
        if (e.isDeadOrDying()) glowK = 1.4F;
        float coreK = state == UnboundObserver.S_MAW ? 1.2F + 0.5F * Mth.sin(t * 0.6F) : 0.6F + 0.2F * Mth.sin(t * 0.11F);

        ps.pushPose();
        ps.translate(0F, rise - kneel - sink, 0F);
        if (shake > 0) ps.translate(Mth.sin(t * 2.3F) * shake, 0F, Mth.cos(t * 2.7F) * shake);
        ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        if (skirt != null) {
            // falda y patas
            ps.pushPose();
            ps.translate(skirt.px, skirt.py, skirt.pz);
            float pulse = 1F + 0.015F * Mth.sin(t * 0.09F);
            ps.scale(pulse, 1F, pulse);
            draw(ps, buf, skirt, false, light, overlay, glowK);
            ps.popPose();
            int li = 0;
            for (BodyMesh.Anchor a : skirt.anchors) {
                if (a.name().equals("leg")) renderLeg(ps, buf, a, skirt, li++, t, walkPos, walkSpd, kneel > 0.1F, overlay);
                else if (a.name().equals("small_eye")) smallEye(ps, buf, skirt, a, t, li * 7 + 3, overlay);
            }

            // torso que se inclina desde la cadera
            ps.pushPose();
            ps.translate(0F, HIP_Y, 0F);
            ps.mulPose(Axis.XP.rotationDegrees(lean(e, age)));
            float breathe = 1F + 0.02F * Mth.sin(t * 0.08F);
            BodyMesh.Part torso = BodyMesh.get("torso"), crown = BodyMesh.get("crown"), maw = BodyMesh.get("maw"), rib = BodyMesh.get("rib");
            ps.pushPose();
            ps.scale(breathe, 1F, breathe);
            draw(ps, buf, torso, false, light, overlay, glowK);
            ps.popPose();
            draw(ps, buf, crown, false, light, overlay, glowK);
            // núcleo de las fauces
            ps.pushPose();
            ps.translate(maw.px, maw.py - HIP_Y, maw.pz);
            float ms = 1F + 0.06F * Mth.sin(t * (state == UnboundObserver.S_MAW ? 0.9F : 0.15F));
            ps.scale(ms, ms, ms);
            draw(ps, buf, maw, false, light, overlay, coreK);
            ps.popPose();
            // costillar partido
            float open = ribOpen(e, age, t);
            for (int side = 1; side >= -1; side -= 2) {
                ps.pushPose();
                ps.translate(side * rib.px, rib.py - HIP_Y, rib.pz);
                if (side < 0) ps.scale(-1F, 1F, 1F);
                ps.mulPose(Axis.YP.rotationDegrees(-open));
                draw(ps, buf, rib, side < 0, light, overlay, glowK);
                ps.popPose();
            }
            // brazos
            for (int side = 1; side >= -1; side -= 2) renderArm(e, ps, buf, side, age, t, walkPos, walkSpd, light, overlay, glowK);
            for (int side = 1; side >= -1; side -= 2) renderSmallArm(ps, buf, side, t, state, light, overlay, glowK);
            // tentáculos de la espalda y la barba
            renderBackTentacles(ps, buf, t, e.isDeadOrDying() || state == VoidEye.S_SCREAM ? 2F : 1F, overlay);
            int fi = 0;
            for (BodyMesh.Anchor a : torso.anchors) {
                if (a.name().equals("face")) renderFaceTentacle(ps, buf, a, fi++, t, state, overlay);
                else smallEye(ps, buf, torso, a, t, fi * 13 + 5, overlay);
            }
            int ci = 0;
            for (BodyMesh.Anchor a : crown.anchors) smallEye(ps, buf, crown, a, t, ci++ * 31 + 11, overlay);
            // halo de esquirlas
            renderHalo(ps, buf, t, state, light, overlay);
            // el Ojo
            renderEye(e, ps, buf, pt, t, age, bodyYaw, overlay);
            ps.popPose();
        } else {
            // sin malla (recursos faltantes): al menos el Ojo
            ps.pushPose();
            ps.translate(0F, HIP_Y, 0F);
            renderEye(e, ps, buf, pt, t, age, bodyYaw, overlay);
            ps.popPose();
        }
        ps.popPose();

        // efectos en coordenadas del mundo
        Vec3 origin = e.getPosition(pt);
        if (state == UnboundObserver.S_SLAM && age < UnboundObserver.SLAM_HIT + 2) renderSlamWarning(e, ps, buf, t, age, origin);
        if (e.isBeamState() && !e.isDeadOrDying()) renderBeam(e, ps, buf, t, age, origin, rise - kneel - sink);
        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    // ------------------------------------------------------------------ piezas

    private static void draw(PoseStack ps, MultiBufferSource buf, BodyMesh.Part part, boolean mirror, int light, int overlay, float glow) {
        if (part == null) return;
        part.render(ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.WHITE)), light, overlay, 1F, 1F, 1F);
        part.renderGlow(ps.last(), buf.getBuffer(RenderType.eyes(VoidEyeRenderer.WHITE)), mirror, glow);
    }

    private static void renderArm(UnboundObserver e, PoseStack ps, MultiBufferSource buf, int side, float age, float t, float walkPos,
                                  float walkSpd, int light, int overlay, float glow) {
        BodyMesh.Part upper = BodyMesh.get("upper_arm"), fore = BodyMesh.get("fore_arm"), hand = BodyMesh.get("hand");
        float[] pose = armPose(e, side, age, t, walkPos, walkSpd);
        boolean mirror = side < 0;
        ps.pushPose();
        ps.translate(side * SHOULDER[0], SHOULDER[1] - HIP_Y, SHOULDER[2]);
        if (mirror) ps.scale(-1F, 1F, 1F);
        ps.mulPose(Axis.ZP.rotationDegrees(pose[1]));
        ps.mulPose(Axis.XP.rotationDegrees(-pose[0]));
        draw(ps, buf, upper, mirror, light, overlay, glow);
        ps.translate(0F, -9.4F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(-pose[2]));
        draw(ps, buf, fore, mirror, light, overlay, glow);
        ps.translate(0F, -9.1F, 0F);
        ps.mulPose(Axis.XP.rotationDegrees(-8F + 6F * Mth.sin(t * 0.07F + side)));
        draw(ps, buf, hand, mirror, light, overlay, glow);
        ps.popPose();
    }

    private static void renderSmallArm(PoseStack ps, MultiBufferSource buf, int side, float t, int state, int light, int overlay, float glow) {
        BodyMesh.Part arm = BodyMesh.get("small_arm");
        boolean mirror = side < 0;
        float twitch = state == UnboundObserver.S_MAW ? 25F * Mth.sin(t * 0.9F + side) : 8F * Mth.sin(t * 0.13F + side * 1.7F)
                + (Mth.sin(t * 0.7F + side) > 0.97F ? 12F : 0F);
        ps.pushPose();
        ps.translate(side * SMALL_ARM[0], SMALL_ARM[1] - HIP_Y, SMALL_ARM[2]);
        if (mirror) ps.scale(-1F, 1F, 1F);
        ps.mulPose(Axis.XP.rotationDegrees(10F + twitch));
        ps.mulPose(Axis.YP.rotationDegrees(-10F + twitch * 0.5F));
        draw(ps, buf, arm, mirror, light, overlay, glow);
        ps.popPose();
    }

    private static void renderHalo(PoseStack ps, MultiBufferSource buf, float t, int state, int light, int overlay) {
        BodyMesh.Part shard = BodyMesh.get("shard");
        if (shard == null) return;
        float speed = state == VoidEye.S_SCREAM || state == UnboundObserver.S_COLOSSAL ? 2.5F : 0.6F;
        ps.pushPose();
        ps.translate(0F, EYE_C[1] - HIP_Y + 0.8F, -4.8F);
        ps.mulPose(Axis.XP.rotationDegrees(-12F));
        for (int i = 0; i < 18; i++) {
            if (i == 3 || i == 9 || i == 10 || i == 15) continue;          // el anillo está roto
            float a = i * 20F + t * speed;
            float r = 10.5F + 0.8F * Mth.sin(i * 2.1F + t * 0.03F);
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(a));
            ps.translate(0F, r, 0F);
            ps.mulPose(Axis.YP.rotationDegrees(t * 1.5F + i * 40));
            float s = 0.9F + 0.5F * ((i * 7) % 4) / 3F;
            ps.scale(s, s, s);
            draw(ps, buf, shard, false, light, overlay, 0F);
            ps.popPose();
        }
        ps.popPose();
    }

    private static void renderLeg(PoseStack ps, MultiBufferSource buf, BodyMesh.Anchor a, BodyMesh.Part skirt, int i, float t, float walkPos,
                                  float walkSpd, boolean spread, int overlay) {
        float x = a.x() + skirt.px, y = a.y() + skirt.py, z = a.z() + skirt.pz;
        float az = (float) Math.toDegrees(Math.atan2(z, x));
        final float phase = i * 0.63F;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(-az));
        ps.mulPose(Axis.ZP.rotationDegrees(spread ? -150F : -162F));
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            TentacleMesh.render(ps, vc, FULL, overlay, 9.8F, a.r() * 1.05F, 16, (k, along) -> new float[]{
                    Mth.sin(walkPos * 0.55F + phase) * 0.08F * walkSpd + 0.025F * Mth.sin(t * 0.06F + phase + k * 0.45F),
                    0.075F + 0.02F * Mth.sin(t * 0.05F + phase)},
                    pass == 0 ? 1F : 0.5F, pass == 0 ? 1F : 0.15F, pass == 0 ? 1F : 0.7F, 1F);
        }
        ps.popPose();
    }

    private static void renderFaceTentacle(PoseStack ps, MultiBufferSource buf, BodyMesh.Anchor a, int i, float t, int state, int overlay) {
        float agit = state == VoidEye.S_SCREAM || state == UnboundObserver.S_MAW ? 2.2F : 1F;
        final float ph = i * 1.37F;
        ps.pushPose();
        ps.translate(a.x(), a.y(), a.z());
        ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, a.nx(), a.ny(), a.nz()));
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            TentacleMesh.render(ps, vc, FULL, overlay, 7.5F + (i % 3), a.r(), 12, (k, along) -> new float[]{
                    0.1F * Mth.sin(t * 0.07F * agit + ph + k * 0.6F) * agit, 0.09F * Mth.cos(t * 0.05F * agit + ph + k * 0.5F) * agit},
                    pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.2F, pass == 0 ? 1F : 0.8F, 1F);
        }
        ps.popPose();
    }

    private static void renderBackTentacles(PoseStack ps, MultiBufferSource buf, float t, float agitation, int overlay) {
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = buf.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(VoidEyeRenderer.TENTACLE) : RenderType.eyes(VoidEyeRenderer.TENTACLE_GLOW));
            for (int i = 0; i < 6; i++) {
                final float ph = i * 1.9F;
                ps.pushPose();
                ps.translate((i - 2.5F) * 1.8F, 22.8F - HIP_Y, -3.4F);
                ps.mulPose(Axis.XP.rotationDegrees(-58F - Math.abs(i - 2.5F) * 8F));
                ps.mulPose(Axis.ZP.rotationDegrees((i - 2.5F) * 16F));
                TentacleMesh.render(ps, vc, FULL, overlay, 16F, 1.1F, 14, (k, along) -> new float[]{
                        (0.13F * Mth.sin(t * 0.05F * agitation + ph + k * 0.5F) + 0.04F) * agitation,
                        0.12F * Mth.cos(t * 0.04F * agitation + ph + k * 0.45F) * agitation},
                        pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.25F, pass == 0 ? 1F : 0.8F, 1F);
                ps.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ ojos

    private static float theta(float v) {
        return v < 0.5F ? v / 0.5F * 0.65F : 0.65F + (v - 0.5F) / 0.5F * (Mth.PI - 0.65F);
    }

    /** Esfera liviana con la textura del globo ocular (polo frontal = +Z). */
    private static void lowSphere(PoseStack.Pose pose, VertexConsumer vc, float R, int overlay, float r, float g, float b) {
        int lat = 10, lon = 16;
        for (int i = 0; i < lat; i++) {
            float v0 = i / (float) lat, v1 = (i + 1) / (float) lat, t0 = theta(v0), t1 = theta(v1);
            for (int j = 0; j < lon; j++) {
                float u0 = j / (float) lon, u1 = (j + 1) / (float) lon;
                sv(pose, vc, R, t0, u0, v0, overlay, r, g, b);
                sv(pose, vc, R, t1, u0, v1, overlay, r, g, b);
                sv(pose, vc, R, t1, u1, v1, overlay, r, g, b);
                sv(pose, vc, R, t0, u1, v0, overlay, r, g, b);
            }
        }
    }

    private static void sv(PoseStack.Pose pose, VertexConsumer vc, float R, float th, float u, float v, int overlay, float r, float g, float b) {
        float ph = u * Mth.TWO_PI;
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.sin(th) * Mth.sin(ph), nz = Mth.cos(th);
        vc.addVertex(pose, nx * R, ny * R, nz * R).setColor(r, g, b, 1F).setUv(u, v).setOverlay(overlay).setLight(FULL).setNormal(pose, nx, ny, nz);
    }

    /** Ojo menor sobre un bulbo: mira para todos lados por su cuenta y parpadea a destiempo. */
    private static void smallEye(PoseStack ps, MultiBufferSource buf, BodyMesh.Part part, BodyMesh.Anchor a, float t, int seed, int overlay) {
        if (!a.name().equals("small_eye")) return;
        float cyc = (t + seed * 17) % (90F + seed % 50);
        float open = cyc < 6 ? Math.abs(cyc - 3F) / 3F : 1F;
        float r = a.r();
        ps.pushPose();
        boolean root = part.name.equals("skirt");               // la falda se dibuja en el marco de los pies
        ps.translate(a.x() + (root ? part.px : 0F), a.y() + (root ? part.py : 0F), a.z() + (root ? part.pz : 0F));
        ps.mulPose(new Quaternionf().rotationTo(0F, 0F, 1F, a.nx(), a.ny(), a.nz()));
        ps.mulPose(Axis.YP.rotationDegrees(25F * Mth.sin(t * 0.05F + seed)));
        ps.mulPose(Axis.XP.rotationDegrees(20F * Mth.sin(t * 0.04F + seed * 0.7F)));
        ps.scale(1F, Math.max(0.08F, open), 1F);
        PoseStack.Pose pose = ps.last();
        lowSphere(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.BALL)), r, overlay, 1F, 1F, 1F);
        lowSphere(pose, buf.getBuffer(RenderType.eyes(VoidEyeRenderer.BALL_GLOW)), r * 1.01F, overlay, 0.9F, 0.4F, 1F);
        VoidEyeRenderer.pupilCap(pose, buf.getBuffer(RenderType.entityCutoutNoCull(VoidEyeRenderer.PUPIL)), r * 1.02F, 0.08F, 0.38F, 0F, 1F, FULL, overlay, 1F, 1F, 1F);
        ps.popPose();
    }

    private static void renderEye(UnboundObserver e, PoseStack ps, MultiBufferSource buf, float pt, float t, float age, float bodyYaw,
                                  int overlay) {
        float R = UnboundObserver.EYE_R;
        ps.pushPose();
        ps.translate(EYE_C[0], EYE_C[1] - HIP_Y, EYE_C[2]);
        float yaw = Mth.rotLerp(pt, e.yawO, e.yaw), pitch = Mth.lerp(pt, e.pitchO, e.pitch);
        float rel = Mth.clamp(Mth.wrapDegrees(yaw - bodyYaw), -60F, 60F);
        ps.mulPose(Axis.YP.rotationDegrees(-rel));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.clamp(pitch, -50F, 60F)));
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

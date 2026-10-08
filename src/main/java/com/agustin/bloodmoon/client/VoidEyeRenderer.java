package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidEye;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * El Observador: globo ocular de 18 bloques con iris púrpura y pupila felina que se contrae o se dilata,
 * párpados de carne negra que se abren y parpadean, ocho tentáculos que se retuercen desde la nuca,
 * tres anillos de runas y un cinturón de Bloques del Vacío que giran a su alrededor.
 * También dibuja la Mirada (rayo), el vórtice de la Singularidad, la onda a ras del piso y la muerte
 * (grietas de luz, rayos y encogimiento hasta un punto).
 */
public class VoidEyeRenderer extends EntityRenderer<VoidEye> {
    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/" + name + ".png");
    }

    static final ResourceLocation BALL = tex("ball"), BALL_GLOW = tex("ball_glow"), CRACKS = tex("cracks"),
            LID = tex("lid"), PUPIL = tex("pupil"), TENTACLE = tex("tentacle"), TENTACLE_GLOW = tex("tentacle_glow"),
            RUNES = tex("runes"), BEAM = tex("beam"), FLARE = tex("flare"), WALL = tex("wave"), VORTEX = tex("vortex"), WHITE = tex("white");

    static final int FULL = LightTexture.FULL_BRIGHT;
    private static final float IRIS_SPLIT = 0.65F;
    private static final float[][] RAYS = new float[12][3];
    private static final float[][] TENTACLES = new float[8][4];

    static {
        RandomSource r = RandomSource.create(1729L);
        for (float[] d : RAYS) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            d[0] = (float) v.x; d[1] = (float) v.y; d[2] = (float) v.z;
        }
        for (int k = 0; k < TENTACLES.length; k++) {
            TENTACLES[k][0] = Mth.TWO_PI * k / TENTACLES.length + r.nextFloat() * 0.3F;   // φ
            TENTACLES[k][1] = 2.25F + r.nextFloat() * 0.35F;                              // θ (detrás)
            TENTACLES[k][2] = 15F + r.nextFloat() * 7F;                                   // largo
            TENTACLES[k][3] = r.nextFloat() * 100F;                                       // fase
        }
    }

    public VoidEyeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(VoidEye entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < 320 * 320;
    }

    @Override
    public ResourceLocation getTextureLocation(VoidEye entity) {
        return BALL;
    }

    // ------------------------------------------------------------------ estado visual

    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** Apertura de párpados 0..1. */
    static float lidOpen(VoidEye e, float age, float t) {
        if (e.isDeadOrDying()) return 0.55F * (1F - smooth((e.deathTime - VoidEye.DEATH_TICKS + 40) / 30F)) + 0.05F * Mth.sin(t * 1.3F);
        int s = e.getState();
        float open = switch (s) {
            case VoidEye.S_AWAKEN -> smooth((age - 105) / 45F);
            case VoidEye.S_GAZE_CHARGE, VoidEye.S_SWEEP_CHARGE -> Mth.lerp(smooth(age / 10F), 1F, 0.55F);   // entrecierra: apunta
            case VoidEye.S_GAZE, VoidEye.S_SWEEP, VoidEye.S_SCREAM -> 1.12F;
            case VoidEye.S_EXPOSED -> 0.72F + 0.06F * Mth.sin(t * 0.15F);
            default -> 1F;
        };
        if (s == VoidEye.S_IDLE || s == VoidEye.S_TENTACLES || s == VoidEye.S_CALL) {   // parpadeo ocasional
            float cyc = (t + e.getId() * 37) % 160F;
            if (cyc < 9) open *= Math.abs(cyc - 4.5F) / 4.5F;
        }
        return open;
    }

    /** {ancho, alto} de la pupila en radianes. */
    static float[] pupil(VoidEye e, float age, float t) {
        if (e.isDeadOrDying()) return new float[]{0.2F + 0.1F * Mth.sin(t), 0.3F};
        float breathe = 1F + 0.08F * Mth.sin(t * 0.07F);
        return switch (e.getState()) {
            case VoidEye.S_GAZE_CHARGE, VoidEye.S_SWEEP_CHARGE -> new float[]{Mth.lerp(smooth(age / 12F), 0.07F, 0.03F), 0.4F};
            case VoidEye.S_GAZE, VoidEye.S_SWEEP -> new float[]{0.035F, 0.42F};
            case VoidEye.S_EXPOSED -> new float[]{0.3F * breathe, 0.33F * breathe};
            case VoidEye.S_SCREAM -> new float[]{0.02F, 0.44F};
            case VoidEye.S_AWAKEN -> new float[]{0.12F, 0.36F};
            default -> new float[]{0.075F * breathe, 0.37F};
        };
    }

    /** Intensidad y tinte del brillo del iris. */
    static float[] glow(VoidEye e, float age, float t) {
        float k;
        int s = e.getState();
        if (e.isDeadOrDying()) k = 1F;
        else k = switch (s) {
            case VoidEye.S_AWAKEN -> 0.15F + 0.75F * smooth((age - 90) / 60F);
            case VoidEye.S_GAZE_CHARGE, VoidEye.S_SWEEP_CHARGE -> 0.6F + 0.4F * smooth(age / 25F) + 0.1F * Mth.sin(t * 1.4F);
            case VoidEye.S_GAZE, VoidEye.S_SWEEP, VoidEye.S_SCREAM -> 1F;
            case VoidEye.S_EXPOSED -> 0.35F + 0.35F * Math.max(0F, Mth.sin(t * 0.31F));
            default -> 0.62F + 0.08F * Mth.sin(t * 0.08F);
        };
        int ph = e.getPhase();
        float r = ph == 3 ? 1F : 0.85F, g = ph == 3 ? 0.3F : 0.45F, b = ph == 3 ? 0.7F : 1F;
        if (e.hurtTime > 0) { r = 1F; g = 0.8F; b = 1F; }
        return new float[]{k * r, k * g, k * b};
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(VoidEye e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        float t = e.tickCount + pt;
        float age = e.clientStateAge(pt);
        float R = VoidEye.RADIUS;
        Vec3 origin = e.getPosition(pt);
        Vec3 camRel = this.entityRenderDispatcher.camera.getPosition().subtract(origin);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0));

        float scale = 1F;
        float shake = 0F;
        if (e.isDeadOrDying()) {
            float d = e.deathTime + pt;
            shake = 0.15F + 0.5F * d / VoidEye.DEATH_TICKS;
            scale = 1F - smooth((d - (VoidEye.DEATH_TICKS - 28)) / 26F) * 0.97F;
        } else if (e.getState() == VoidEye.S_SCREAM) {
            shake = 0.35F;
        } else if (e.getState() == VoidEye.S_AWAKEN && age > 60) {
            shake = 0.12F * smooth((age - 60) / 60F);
        }

        ps.pushPose();
        ps.translate(0F, R, 0F);
        if (shake > 0) {
            ps.translate(Mth.sin(t * 2.3F) * shake, Mth.sin(t * 3.1F + 1) * shake, Mth.cos(t * 2.7F) * shake);
        }

        // ---------------- anillos y bloques que orbitan (no siguen la mirada)
        if (!e.isDeadOrDying()) {
            float ringK = e.getState() == VoidEye.S_AWAKEN ? smooth((age - 40) / 80F) : 1F;
            renderRunes(e, ps, buf, t, ringK);
            renderOrbitingBlocks(ps, buf, t, ringK);
        }

        // ---------------- el ojo
        ps.pushPose();
        float yaw = Mth.rotLerp(pt, e.yawO, e.yaw), pitch = Mth.lerp(pt, e.pitchO, e.pitch);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.scale(scale, scale, scale);

        float[] gl = glow(e, age, t);
        float[] pu = pupil(e, age, t);
        PoseStack.Pose pose = ps.last();
        sphere(pose, buf.getBuffer(RenderType.entityCutoutNoCull(BALL)), R, FULL, overlay, 1F, 1F, 1F);
        sphere(pose, buf.getBuffer(RenderType.eyes(BALL_GLOW)), R * 1.001F, FULL, overlay, gl[0], gl[1], gl[2]);
        pupilCap(pose, buf.getBuffer(RenderType.entityCutoutNoCull(PUPIL)), R * 1.004F, pu[0], pu[1], 0F, 1F, FULL, overlay, 1F, 1F, 1F);
        if (e.isBeamState()) {   // borde incandescente de la pupila mientras apunta y dispara
            float k = e.isFiring() ? 1F : smooth(age / 20F);
            pupilCap(pose, buf.getBuffer(RenderType.eyes(WHITE)), R * 1.005F, pu[0], pu[1], 1F, 1.9F, FULL, overlay, k, 0.55F * k, k);
        }
        if (e.isDeadOrDying()) {
            float k = smooth(e.deathTime / (float) (VoidEye.DEATH_TICKS - 30));
            sphere(pose, buf.getBuffer(RenderType.eyes(CRACKS)), R * 1.006F, FULL, overlay, k, k * 0.9F, k);
        }

        float open = lidOpen(e, age, t);
        VertexConsumer lid = buf.getBuffer(RenderType.entityCutoutNoCull(LID));
        lid(ps, lid, R, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 62F), true, overlay);
        lid(ps, lid, R * 1.006F, Mth.lerp(Mth.clamp(open, 0F, 1.2F), 0F, 48F), false, overlay);   // un poco por fuera: evita z-fighting atrás

        renderBackTentacles(e, ps, buf, t, R, overlay);
        ps.popPose();

        if (e.isDeadOrDying()) renderDeathRays(e, ps, buf, pt, camRel.subtract(0, R, 0));
        ps.popPose();

        // ---------------- ataques en coordenadas del mundo (relativas a la entidad)
        if (e.isBeamState() && !e.isDeadOrDying()) renderBeam(e, ps, buf, pt, t, age, origin, camRel);
        int s = e.getState();
        if (s == VoidEye.S_PULL || (s == VoidEye.S_WAVE && age < 12)) renderVortex(e, ps, buf, t, age, origin);
        if (s == VoidEye.S_WAVE) renderWave(e, ps, buf, age, origin);

        super.render(e, entityYaw, pt, ps, buf, packedLight);
    }

    // ------------------------------------------------------------------ globo ocular

    /** v de la textura -> ángulo desde el polo frontal (la mitad superior de la textura es el iris). */
    private static float theta(float v) {
        return v < 0.5F ? v / 0.5F * IRIS_SPLIT : IRIS_SPLIT + (v - 0.5F) / 0.5F * (Mth.PI - IRIS_SPLIT);
    }

    static void sphere(PoseStack.Pose pose, VertexConsumer vc, float R, int light, int overlay, float r, float g, float b) {
        int lat = 40, lon = 48;
        for (int i = 0; i < lat; i++) {
            float v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
            float t0 = theta(v0), t1 = theta(v1);
            for (int j = 0; j < lon; j++) {
                float u0 = j / (float) lon, u1 = (j + 1) / (float) lon;
                sv(pose, vc, R, t0, u0 * Mth.TWO_PI, u0, v0, light, overlay, r, g, b);
                sv(pose, vc, R, t1, u0 * Mth.TWO_PI, u0, v1, light, overlay, r, g, b);
                sv(pose, vc, R, t1, u1 * Mth.TWO_PI, u1, v1, light, overlay, r, g, b);
                sv(pose, vc, R, t0, u1 * Mth.TWO_PI, u1, v0, light, overlay, r, g, b);
            }
        }
    }

    private static void sv(PoseStack.Pose pose, VertexConsumer vc, float R, float th, float ph, float u, float v, int light, int overlay,
                           float r, float g, float b) {
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.sin(th) * Mth.sin(ph), nz = Mth.cos(th);
        vc.addVertex(pose, nx * R, ny * R, nz * R).setColor(r, g, b, 1F).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Elipse sobre la esfera (anillo de s0 a s1 veces el tamaño), centrada en el polo frontal. */
    static void pupilCap(PoseStack.Pose pose, VertexConsumer vc, float R, float w, float h, float s0, float s1, int light, int overlay,
                                 float r, float g, float b) {
        int rings = 6, seg = 36;
        for (int i = 0; i < rings; i++) {
            float a0 = s0 + (s1 - s0) * i / rings, a1 = s0 + (s1 - s0) * (i + 1) / rings;
            for (int j = 0; j < seg; j++) {
                float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
                cv(pose, vc, R, a0 * w * Mth.cos(p0), a0 * h * Mth.sin(p0), light, overlay, r, g, b);
                cv(pose, vc, R, a1 * w * Mth.cos(p0), a1 * h * Mth.sin(p0), light, overlay, r, g, b);
                cv(pose, vc, R, a1 * w * Mth.cos(p1), a1 * h * Mth.sin(p1), light, overlay, r, g, b);
                cv(pose, vc, R, a0 * w * Mth.cos(p1), a0 * h * Mth.sin(p1), light, overlay, r, g, b);
            }
        }
    }

    private static void cv(PoseStack.Pose pose, VertexConsumer vc, float R, float ax, float ay, int light, int overlay, float r, float g, float b) {
        float th = Mth.sqrt(ax * ax + ay * ay);
        float ph = (float) Mth.atan2(ay, ax);
        float nx = Mth.sin(th) * Mth.cos(ph), ny = Mth.sin(th) * Mth.sin(ph), nz = Mth.cos(th);
        vc.addVertex(pose, nx * R, ny * R, nz * R).setColor(r, g, b, 1F).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Medio cascarón de párpado; ρ = grados que se retira hacia atrás (0 = cerrado). */
    static void lid(PoseStack ps, VertexConsumer vc, float R, float rho, boolean upper, int overlay) {
        ps.pushPose();
        if (!upper) ps.scale(1F, -1F, 1F);
        ps.mulPose(Axis.XP.rotationDegrees(-rho));
        PoseStack.Pose pose = ps.last();
        int na = 26, nb = 26;
        float aMin = -0.09F;
        for (int i = 0; i < na; i++) {
            float a0 = aMin + (Mth.PI - aMin) * i / na, a1 = aMin + (Mth.PI - aMin) * (i + 1) / na;
            for (int j = 0; j < nb; j++) {
                float b0 = -Mth.HALF_PI + Mth.PI * j / nb, b1 = -Mth.HALF_PI + Mth.PI * (j + 1) / nb;
                lv(pose, vc, R, a0, b0, aMin, overlay);
                lv(pose, vc, R, a1, b0, aMin, overlay);
                lv(pose, vc, R, a1, b1, aMin, overlay);
                lv(pose, vc, R, a0, b1, aMin, overlay);
            }
        }
        ps.popPose();
    }

    private static void lv(PoseStack.Pose pose, VertexConsumer vc, float R, float a, float b, float aMin, int overlay) {
        float rr;
        if (a < 0) rr = R * Mth.lerp((a - aMin) / -aMin, 1.0F, 1.1F);                 // labio que se mete bajo el borde
        else rr = R * (1.045F + 0.06F * (float) Math.exp(-a * 7F));                       // reborde grueso
        float x = Mth.sin(b), y = Mth.cos(b) * Mth.sin(a), z = Mth.cos(b) * Mth.cos(a);
        float shade = a < 0.05F ? 0.55F : 1F;
        vc.addVertex(pose, x * rr, y * rr, z * rr).setColor(shade, shade, shade, 1F)
                .setUv((b + Mth.HALF_PI) / Mth.PI, (a - aMin) / (Mth.PI - aMin)).setOverlay(overlay).setLight(FULL).setNormal(pose, x, y, z);
    }

    // ------------------------------------------------------------------ tentáculos de la nuca

    static void renderBackTentacles(VoidEye e, PoseStack ps, MultiBufferSource buf, float t, float R, int overlay) {
        VertexConsumer body = buf.getBuffer(RenderType.entityCutoutNoCull(TENTACLE));
        boolean awake = e.getState() != VoidEye.S_AWAKEN;
        float agitation = e.isDeadOrDying() ? 2.2F : e.getState() == VoidEye.S_SCREAM ? 2F : e.isFiring() ? 1.4F : 1F;
        for (int pass = 0; pass < 2; pass++) {
            VertexConsumer vc = pass == 0 ? body : buf.getBuffer(RenderType.eyes(TENTACLE_GLOW));
            for (float[] tn : TENTACLES) {
                float th = tn[1], ph = tn[0];
                Vector3f n = new Vector3f(Mth.sin(th) * Mth.cos(ph), Mth.sin(th) * Mth.sin(ph), Mth.cos(th));
                ps.pushPose();
                ps.translate(n.x() * R * 0.97F, n.y() * R * 0.97F, n.z() * R * 0.97F);
                ps.mulPose(new Quaternionf().rotationTo(0F, 1F, 0F, n.x(), n.y(), n.z()));
                final float phase = tn[3];
                final float len = tn[2] * (awake ? 1F : 0.85F);
                TentacleMesh.render(ps, vc, FULL, overlay, len, 1.15F, 16, (i, along) -> new float[]{
                        (0.12F * Mth.sin(t * 0.05F * agitation + phase + i * 0.5F) + 0.05F) * agitation,
                        0.14F * Mth.cos(t * 0.04F * agitation + phase * 1.3F + i * 0.42F) * agitation},
                        pass == 0 ? 1F : 0.55F, pass == 0 ? 1F : 0.25F, pass == 0 ? 1F : 0.8F, 1F);
                ps.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ anillos y bloques en órbita

    static void renderRunes(VoidEye e, PoseStack ps, MultiBufferSource buf, float t, float k) {
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(RUNES));
        float[][] rings = {{13.5F, 24F, 0.6F, 1.6F}, {16F, -62F, -0.4F, 1.2F}, {19F, 75F, 0.25F, 1.0F}};
        boolean fast = e.getPhase() == 3;
        for (int i = 0; i < rings.length; i++) {
            float[] rg = rings[i];
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.3F * (i + 1)));
            ps.mulPose(Axis.XP.rotationDegrees(rg[1] + 6F * Mth.sin(t * 0.01F + i)));
            ps.mulPose(Axis.YP.rotationDegrees(t * rg[2] * (fast ? 2.2F : 1F)));
            float bright = k * (0.55F + 0.2F * Mth.sin(t * 0.07F + i * 2));
            float r = fast ? bright : bright * 0.7F, g = bright * 0.3F, b = bright;
            band(ps.last(), vc, rg[0], rg[3], 72, 10, r, g, b, 0F);
            ps.popPose();
        }
    }

    /** Banda cilíndrica vertical (alto h) de radio r, visible de ambos lados. */
    static void band(PoseStack.Pose pose, VertexConsumer vc, float radius, float h, int seg, float uRepeat,
                             float r, float g, float b, float y0) {
        for (int j = 0; j < seg; j++) {
            float a0 = Mth.TWO_PI * j / seg, a1 = Mth.TWO_PI * (j + 1) / seg;
            float u0 = uRepeat * j / seg, u1 = uRepeat * (j + 1) / seg;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float ya = y0 - (y0 == 0F ? h / 2 : 0F), yb = ya + h;
            quad2(pose, vc, x0, ya, z0, x1, ya, z1, x1, yb, z1, x0, yb, z0, u0, u1, r, g, b);
        }
    }

    /** Cuadrilátero con las dos caras (los tipos aditivos descartan la cara trasera). */
    static void quad2(PoseStack.Pose pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz, float u0, float u1, float r, float g, float b) {
        add(pose, vc, ax, ay, az, u0, 1, r, g, b);
        add(pose, vc, bx, by, bz, u1, 1, r, g, b);
        add(pose, vc, cx, cy, cz, u1, 0, r, g, b);
        add(pose, vc, dx, dy, dz, u0, 0, r, g, b);
        add(pose, vc, dx, dy, dz, u0, 0, r, g, b);
        add(pose, vc, cx, cy, cz, u1, 0, r, g, b);
        add(pose, vc, bx, by, bz, u1, 1, r, g, b);
        add(pose, vc, ax, ay, az, u0, 1, r, g, b);
    }

    private static void add(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, float r, float g, float b) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 1F).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(pose, 0F, 1F, 0F);
    }

    static void renderOrbitingBlocks(PoseStack ps, MultiBufferSource buf, float t, float k) {
        if (k <= 0.01F) return;
        var blocks = Minecraft.getInstance().getBlockRenderer();
        var state = ModBlocks.VOID_BLOCK.get().defaultBlockState();
        int n = 16;
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(-18F));
        ps.mulPose(Axis.ZP.rotationDegrees(9F));
        for (int i = 0; i < n; i++) {
            float a = Mth.TWO_PI * i / n + t * 0.008F * (1F + (i % 3) * 0.35F);
            float rad = (16.5F + 2.5F * Mth.sin(i * 2.1F)) * (0.6F + 0.4F * k);
            float s = (0.9F + 0.7F * ((i * 7) % 5) / 4F) * k;
            ps.pushPose();
            ps.translate(Mth.cos(a) * rad, Mth.sin(t * 0.03F + i) * 1.5F, Mth.sin(a) * rad);
            ps.mulPose(Axis.XP.rotationDegrees(t * (1.2F + i % 4)));
            ps.mulPose(Axis.YP.rotationDegrees(t * (0.8F + i % 3) + i * 40));
            ps.scale(s, s, s);
            ps.translate(-0.5F, -0.5F, -0.5F);
            blocks.renderSingleBlock(state, ps, buf, FULL, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------ Mirada

    static void ribbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float width, float vScroll,
                               float r, float g, float bl) {
        Vec3 axis = b.subtract(a);
        double len = axis.length();
        if (len < 1e-3) return;
        Vec3 toCam = cam.subtract(a.add(b).scale(0.5));
        Vec3 side = axis.cross(toCam);
        if (side.lengthSqr() < 1e-6) side = axis.cross(new Vec3(0, 1, 0));
        side = side.normalize().scale(width / 2);
        float v0 = vScroll, v1 = vScroll + (float) (len / 6.0);
        Vec3 p0 = a.subtract(side), p1 = a.add(side), p2 = b.add(side), p3 = b.subtract(side);
        ribbonVertex(pose, vc, p0, 0, v0, r, g, bl);
        ribbonVertex(pose, vc, p1, 1, v0, r, g, bl);
        ribbonVertex(pose, vc, p2, 1, v1, r, g, bl);
        ribbonVertex(pose, vc, p3, 0, v1, r, g, bl);
        ribbonVertex(pose, vc, p3, 0, v1, r, g, bl);
        ribbonVertex(pose, vc, p2, 1, v1, r, g, bl);
        ribbonVertex(pose, vc, p1, 1, v0, r, g, bl);
        ribbonVertex(pose, vc, p0, 0, v0, r, g, bl);
    }

    private static void ribbonVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, float r, float g, float b) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 1F).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(pose, 0F, 1F, 0F);
    }

    private void billboard(PoseStack ps, VertexConsumer vc, Vec3 at, float size, float spin, float r, float g, float b) {
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        ps.scale(size, size, size);
        PoseStack.Pose pose = ps.last();
        quad2(pose, vc, -0.5F, -0.5F, 0, 0.5F, -0.5F, 0, 0.5F, 0.5F, 0, -0.5F, 0.5F, 0, 0, 1, r, g, b);
        ps.popPose();
    }

    private void renderBeam(VoidEye e, PoseStack ps, MultiBufferSource buf, float pt, float t, float age, Vec3 origin, Vec3 camRel) {
        Vec3 center = new Vec3(0, VoidEye.RADIUS, 0);
        Vec3 end = e.getBeamEnd().subtract(origin);
        Vec3 dir = end.subtract(center).normalize();
        Vec3 start = center.add(dir.scale(VoidEye.RADIUS * 1.01));
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(BEAM));
        PoseStack.Pose pose = ps.last();
        float scroll = -t * 0.35F;
        boolean phase3 = e.getPhase() == 3;
        if (!e.isFiring()) {
            float flick = 0.5F + 0.5F * Mth.sin(t * 2.1F) * Mth.sin(t * 0.7F);
            float k = smooth(age / 10F) * (0.55F + 0.45F * flick);
            ribbon(pose, vc, start, end, camRel, 0.14F, scroll, 0.75F * k, 0.25F * k, k);
            VertexConsumer fl = buf.getBuffer(RenderType.eyes(FLARE));
            billboard(ps, fl, end, 1.6F + flick, t * 4, 0.6F * k, 0.15F * k, 0.9F * k);
            return;
        }
        float in = smooth(age / 4F);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 1.7F);
        float rr = phase3 ? 1F : 0.65F, gg = phase3 ? 0.25F : 0.2F;
        ribbon(pose, vc, start, end, camRel, 4.2F * in * pulse, scroll * 0.6F, 0.22F * rr, 0.05F, 0.35F);
        ribbon(pose, vc, start, end, camRel, 1.9F * in * pulse, scroll, 0.7F * rr, gg, 1F);
        ribbon(pose, vc, start, end, camRel, 0.6F * in, scroll * 1.5F, 1F, 0.85F, 1F);
        VertexConsumer fl = buf.getBuffer(RenderType.eyes(FLARE));
        billboard(ps, fl, end, 5F * pulse * in, t * 6, 0.9F, 0.35F, 1F);
        billboard(ps, fl, start, 7F * pulse * in, -t * 3, 0.8F, 0.3F, 1F);
    }

    // ------------------------------------------------------------------ Singularidad y onda

    private static void renderVortex(VoidEye e, PoseStack ps, MultiBufferSource buf, float t, float age, Vec3 origin) {
        Vec3 h = e.getHome().subtract(origin);
        float k = e.getState() == VoidEye.S_PULL ? smooth(age / 30F) : 1F - age / 12F;
        float size = 46F;
        ps.pushPose();
        ps.translate(h.x, h.y + 0.08, h.z);
        ps.mulPose(Axis.YP.rotationDegrees(t * (6F + 10F * k)));
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(VORTEX));
        PoseStack.Pose pose = ps.last();
        float c = 0.8F * k;
        quad2(pose, vc, -size, 0, -size, size, 0, -size, size, 0, size, -size, 0, size, 0, 1, c * 0.7F, c * 0.2F, c);
        ps.popPose();
    }

    private static void renderWave(VoidEye e, PoseStack ps, MultiBufferSource buf, float age, Vec3 origin) {
        Vec3 h = e.getHome().subtract(origin);
        float radius = (float) (VoidEye.WAVE_START + age * VoidEye.WAVE_SPEED);
        float fade = 1F - smooth((age - (VoidEye.WAVE_TICKS - 12)) / 12F);
        ps.pushPose();
        ps.translate(h.x, h.y, h.z);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(WALL));
        band(ps.last(), vc, radius, 1.1F, 128, 24, 0.9F * fade, 0.35F * fade, 1F * fade, 0.001F);
        band(ps.last(), vc, radius + 0.4F, 2.6F, 128, 24, 0.3F * fade, 0.08F * fade, 0.45F * fade, 0.001F);
        ps.popPose();
    }

    // ------------------------------------------------------------------ muerte

    private void renderDeathRays(VoidEye e, PoseStack ps, MultiBufferSource buf, float pt, Vec3 camRel) {
        float d = e.deathTime + pt;
        float k = smooth(d / (VoidEye.DEATH_TICKS - 20F));
        if (d > VoidEye.DEATH_TICKS - 8) k *= Math.max(0F, (VoidEye.DEATH_TICKS - d) / 8F);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(BEAM));
        PoseStack.Pose pose = ps.last();
        for (int i = 0; i < RAYS.length; i++) {
            float[] r = RAYS[i];
            float grow = smooth((d - i * 6) / 30F);
            if (grow <= 0) continue;
            Vec3 dir = new Vec3(r[0], r[1], r[2]);
            Vec3 a = dir.scale(VoidEye.RADIUS * 0.6), b = dir.scale(VoidEye.RADIUS + 45 * grow);
            ribbon(pose, vc, a, b, camRel, (0.6F + 2.2F * grow) * k, -d * 0.2F, k, 0.85F * k, k);
        }
        VertexConsumer fl = buf.getBuffer(RenderType.eyes(FLARE));
        billboard(ps, fl, Vec3.ZERO, (14F + 20F * k) * (d > VoidEye.DEATH_TICKS - 26 ? 1.6F : 1F), d * 3, k, 0.9F * k, k);
    }
}

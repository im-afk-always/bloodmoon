package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Noche sin luna: una grieta que cruza el cielo de este a oeste por el cenit y se afina hasta terminar
 * en punta sobre cada horizonte. Dentro, fijo en el cenit, un ojo púrpura de pupila felina (vertical,
 * sobre el lado corto) que barre de lado a lado. Toda la luz queda dentro de la grieta: afuera, negro.
 * Se dibuja después del cielo vanilla y antes del terreno, así que montañas y techos lo tapan.
 */
public final class MoonlessSkyRenderer {
    private static final float R = 100F;           // radio de la "cúpula"
    private static final int SEG = 120;            // segmentos a lo largo de la grieta
    private static final float THETA_MAX = 88F;    // grados desde el cenit: las puntas tocan el horizonte
    private static final float RIFT_W = 20F;       // media apertura máxima de la grieta (grados)

    private static final float EYE_L = 18F;        // medio largo del ojo (grados)
    private static final float EYE_H = 10F;        // medio alto del ojo (grados)
    private static final float EYE_THETA = 0F;     // posición fija del ojo en la grieta (0 = cenit)
    private static final float PUPIL_SWEEP = 0.5F; // la pupila barre ±50% del largo del ojo
    private static final float PUPIL_PERIOD = 200F; // ticks de ida y vuelta (10 s)
    private static final int ACROSS = 10;          // subdivisiones a través de la grieta (degradado de luz)
    private static final float PR = 0.63F, PG = 0.16F, PB = 1F; // púrpura

    private MoonlessSkyRenderer() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (ClientMoonState.visual() != MoonType.MOONLESS) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = ClientMoonState.intensity(pt);
        if (k <= 0F) return;
        float time = level.getGameTime() + pt;
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Eye eye = new Eye(EYE_THETA);

        RenderSystem.defaultBlendFunc();
        drawRiftInterior(mv, eye, k);
        drawLids(mv, eye, k);
        drawIris(mv, eye, k);
        drawPupil(mv, eye, k, time);
        additive();
        drawGlint(mv, eye, k);

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void additive() { // brillo del reflejo
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    // ------------------------------------------------------------------ grieta

    private static float hash(float x) {
        float v = Mth.sin(x * 12.9898F) * 43758.5453F;
        return v - Mth.floor(v);
    }

    private static float smoothNoise(float x, float seed) {
        int i = Mth.floor(x);
        float f = x - i;
        f = f * f * (3F - 2F * f);
        float a = hash(i * 1.7F + seed), b = hash((i + 1) * 1.7F + seed);
        return a + (b - a) * f;
    }

    private static float theta(int i) {
        return -THETA_MAX + 2F * THETA_MAX * i / SEG;
    }

    /** Media apertura de un borde (side = +1 / -1): irregular y con ancho 0 en las puntas (horizontes). */
    private static float halfWidth(int i, int side) {
        float th = theta(i);
        float base = (float) Math.pow(Math.max(0F, 1F - (th / THETA_MAX) * (th / THETA_MAX)), 0.85);
        float seed = side > 0 ? 3.1F : 11.7F;
        float n = 0.75F * smoothNoise(i / 5F, seed) + 0.25F * hash(i * 2.3F + seed);
        return RIFT_W * base * (0.78F + 0.44F * n);
    }

    /** Punto sobre la cúpula: theta a lo largo de la grieta, phi a través (hacia el sur/norte). */
    private static Vector3f riftPoint(float thetaDeg, float phiDeg) {
        float th = thetaDeg * Mth.DEG_TO_RAD, ph = phiDeg * Mth.DEG_TO_RAD;
        float c = Mth.cos(ph);
        return new Vector3f(Mth.sin(th) * c * R, Mth.cos(th) * c * R, Mth.sin(ph) * R);
    }

    /**
     * Interior de la grieta: negro, iluminado solo por el ojo y por un brillo que nace en el labio.
     * No se dibuja nada fuera del labio, así que el cielo exterior queda completamente oscuro.
     */
    private static void drawRiftInterior(Matrix4f mv, Eye eye, float k) {
        BufferBuilder bb = begin();
        for (int i = 0; i < SEG; i++) {
            float t0 = theta(i), t1 = theta(i + 1);
            for (int j = 0; j < ACROSS; j++) {
                float a0 = -1F + 2F * j / ACROSS, a1 = -1F + 2F * (j + 1) / ACROSS;
                riftVertex(bb, mv, eye, t0, across(i, a0), Math.abs(a0), k);
                riftVertex(bb, mv, eye, t1, across(i + 1, a0), Math.abs(a0), k);
                riftVertex(bb, mv, eye, t1, across(i + 1, a1), Math.abs(a1), k);
                riftVertex(bb, mv, eye, t0, across(i, a1), Math.abs(a1), k);
            }
        }
        draw(bb);
    }

    /** a ∈ [-1, 1] de un labio al otro. */
    private static float across(int i, float a) {
        return a * (a >= 0 ? halfWidth(i, 1) : halfWidth(i, -1));
    }

    private static void riftVertex(BufferBuilder bb, Matrix4f mv, Eye eye, float thetaDeg, float phiDeg,
                                   float edge, float k) {
        Vector3f p = riftPoint(thetaDeg, phiDeg);
        float cos = Mth.clamp(p.dot(eye.center()) / R, -1F, 1F);
        float angle = (float) Math.toDegrees(Math.acos(cos));
        float eyeLight = 0.9F * (float) Math.exp(-(angle / 18F) * (angle / 18F));
        float lip = Math.max(0F, (edge - 0.55F) / 0.45F);
        float light = Math.min(1.2F, eyeLight + 0.85F * lip * lip);
        vertex(bb, mv, p, Math.min(1F, PR * light), Math.min(1F, PG * light), Math.min(1F, PB * light), k);
    }

    // ------------------------------------------------------------------ ojo

    /** Plano tangente en el centro del ojo: u a lo largo de la grieta, v a través. */
    private record Eye(Vector3f center, Vector3f u, Vector3f v) {
        Eye(float thetaDeg) {
            this(new Vector3f(Mth.sin(thetaDeg * Mth.DEG_TO_RAD), Mth.cos(thetaDeg * Mth.DEG_TO_RAD), 0F),
                    new Vector3f(Mth.cos(thetaDeg * Mth.DEG_TO_RAD), -Mth.sin(thetaDeg * Mth.DEG_TO_RAD), 0F),
                    new Vector3f(0F, 0F, 1F));
        }

        Vector3f point(float uDeg, float vDeg) {
            float tu = (float) Math.tan(uDeg * Mth.DEG_TO_RAD), tv = (float) Math.tan(vDeg * Mth.DEG_TO_RAD);
            return new Vector3f(center).add(u.x * tu + v.x * tv, u.y * tu + v.y * tv, u.z * tu + v.z * tv)
                    .normalize().mul(R);
        }
    }

    /** Media altura del almendrado en la posición s ∈ [-1, 1]. */
    private static float lid(float s) {
        return EYE_H * (float) Math.pow(Math.max(0F, 1F - s * s), 0.75);
    }

    private static void drawLids(Matrix4f mv, Eye eye, float k) {
        BufferBuilder bb = begin();
        int n = 48;
        for (int i = 0; i < n; i++) {
            float s0 = -1F + 2F * i / n, s1 = -1F + 2F * (i + 1) / n;
            for (int sg = -1; sg <= 1; sg += 2) {
                vertex(bb, mv, eye.point(EYE_L * s0, sg * lid(s0)), 0.06F, 0F, 0.1F, k);
                vertex(bb, mv, eye.point(EYE_L * s1, sg * lid(s1)), 0.06F, 0F, 0.1F, k);
                vertex(bb, mv, eye.point(EYE_L * s1, sg * (lid(s1) * 1.12F + 0.4F)), 0.06F, 0F, 0.1F, k);
                vertex(bb, mv, eye.point(EYE_L * s0, sg * (lid(s0) * 1.12F + 0.4F)), 0.06F, 0F, 0.1F, k);
            }
        }
        draw(bb);
    }

    private static void drawIris(Matrix4f mv, Eye eye, float k) {
        BufferBuilder bb = begin();
        int ns = 48, nt = 16;
        for (int i = 0; i < ns; i++) {
            float s0 = -1F + 2F * i / ns, s1 = -1F + 2F * (i + 1) / ns;
            for (int j = 0; j < nt; j++) {
                float t0 = -1F + 2F * j / nt, t1 = -1F + 2F * (j + 1) / nt;
                irisVertex(bb, mv, eye, EYE_L * s0, lid(s0) * t0, t0, k);
                irisVertex(bb, mv, eye, EYE_L * s1, lid(s1) * t0, t0, k);
                irisVertex(bb, mv, eye, EYE_L * s1, lid(s1) * t1, t1, k);
                irisVertex(bb, mv, eye, EYE_L * s0, lid(s0) * t1, t1, k);
            }
        }
        draw(bb);
    }

    /** Iris felino: núcleo brillante, violeta intenso, borde oscuro, fibras radiales y sombra del párpado. */
    private static void irisVertex(BufferBuilder bb, Matrix4f mv, Eye eye, float u, float v, float t, float k) {
        float d = Mth.sqrt(u * u + v * v) / EYE_H;
        float phi = (float) Mth.atan2(v, u);
        float r, g, b;
        if (d < 1F) {
            float x = d * d * (3F - 2F * d);
            r = Mth.lerp(x, 1.0F, 0.72F); g = Mth.lerp(x, 0.62F, 0.12F); b = Mth.lerp(x, 1.0F, 0.95F);
        } else {
            float x = Math.min(1F, (d - 1F) / 1.2F);
            r = Mth.lerp(x, 0.72F, 0.22F); g = Mth.lerp(x, 0.12F, 0F); b = Mth.lerp(x, 0.95F, 0.32F);
        }
        float shade = (0.80F + 0.20F * Mth.sin(phi * 18F + d * 3F)) * (1F - 0.6F * t * t * t * t);
        vertex(bb, mv, eye.point(u, v), r * shade, g * shade, b * shade, k);
    }

    /**
     * Pupila felina: rendija sobre el lado corto del ojo (a través de la grieta) que barre de lado a lado
     * a lo largo del ojo, frenando en los extremos. Se acorta cerca de las puntas para no salir de los párpados.
     */
    private static void drawPupil(Matrix4f mv, Eye eye, float k, float time) {
        float u0 = PUPIL_SWEEP * EYE_L * Mth.sin(time * Mth.TWO_PI / PUPIL_PERIOD);
        float width = 0.17F * EYE_H * (1F + 0.2F * Mth.sin(time * 0.02F)); // "respira"
        float halfLen = 0.95F * lid(u0 / EYE_L);
        BufferBuilder bb = begin();
        int n = 24;
        for (int j = 0; j < n; j++) {
            float t0 = -1F + 2F * j / n, t1 = -1F + 2F * (j + 1) / n;
            float w0 = width * (float) Math.pow(Math.max(0F, 1F - t0 * t0), 0.9);
            float w1 = width * (float) Math.pow(Math.max(0F, 1F - t1 * t1), 0.9);
            float v0 = halfLen * t0, v1 = halfLen * t1;
            vertex(bb, mv, eye.point(u0 - w0, insideLids(u0 - w0, v0)), 0F, 0F, 0F, k);
            vertex(bb, mv, eye.point(u0 + w0, insideLids(u0 + w0, v0)), 0F, 0F, 0F, k);
            vertex(bb, mv, eye.point(u0 + w1, insideLids(u0 + w1, v1)), 0F, 0F, 0F, k);
            vertex(bb, mv, eye.point(u0 - w1, insideLids(u0 - w1, v1)), 0F, 0F, 0F, k);
        }
        draw(bb);
    }

    private static float insideLids(float u, float v) {
        float max = 0.9F * lid(u / EYE_L);
        return Mth.clamp(v, -max, max);
    }

    private static void drawGlint(Matrix4f mv, Eye eye, float k) {
        BufferBuilder bb = begin();
        float gx = -0.32F * EYE_H, gy = -0.38F * EYE_H, gr = 0.13F * EYE_H;
        int n = 20;
        Vector3f c = eye.point(gx, gy);
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            vertex(bb, mv, c, 1F, 0.92F, 1F, 0.45F * k);
            vertex(bb, mv, c, 1F, 0.92F, 1F, 0.45F * k);
            vertex(bb, mv, eye.point(gx + gr * Mth.cos(a1), gy + gr * 0.7F * Mth.sin(a1)), 1F, 0.92F, 1F, 0F);
            vertex(bb, mv, eye.point(gx + gr * Mth.cos(a0), gy + gr * 0.7F * Mth.sin(a0)), 1F, 0.92F, 1F, 0F);
        }
        draw(bb);
    }

    // ------------------------------------------------------------------ util

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void vertex(BufferBuilder bb, Matrix4f mv, Vector3f p, float r, float g, float b, float a) {
        bb.addVertex(mv, p.x, p.y, p.z).setColor(r, g, b, a);
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}

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
 * Noche sin luna, en etapas (hora del día, ticks):
 * <pre>
 * 13000-13600  el cielo se apaga lentamente hasta negro total (ClientMoonState)
 * 13600-14200  oscuridad absoluta
 * 14200-14800  una ruptura brillante nace en el cenit y se propaga hacia ambos horizontes
 * 14800-16000  la grieta se abre; dentro aparece el ojo, cerrado
 * 16000-16300  el ojo se abre; luego frunce la mirada en bucle (pupila quieta)
 * 21700-23000  todo se revierte: el ojo se cierra, la grieta se sella y la ruptura se retrae
 * </pre>
 * Toda la luz queda dentro del labio: afuera, negro. Se dibuja antes del terreno.
 */
public final class MoonlessSkyRenderer {
    private static final float R = 100F;
    private static final int SEG = 120;
    private static final float THETA_MAX = 88F;    // las puntas tocan el horizonte
    private static final float RIFT_W = 20F;       // media apertura máxima (grados)
    private static final int ACROSS = 10;

    private static final float EYE_L = 18F;        // medio largo del ojo (grados)
    private static final float EYE_H = 10F;        // medio alto del ojo (grados)
    private static final float EYE_THETA = 0F;     // posición fija (0 = cenit)

    private static final float SQUINT_PERIOD = 240F; // ciclo de fruncir la mirada (12 s)
    private static final float PR = 0.63F, PG = 0.16F, PB = 1F;

    private MoonlessSkyRenderer() {}

    /** Estado de la secuencia para un instante dado. */
    private record Stage(float crack, float open, float eyeOpen, float squint) {}

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static Stage stage(float tod, float time) {
        float crack = smooth((tod - 14200F) / 600F) * (1F - smooth((tod - 22700F) / 300F));
        float open = smooth((tod - 14800F) / 1200F) * (1F - smooth((tod - 22000F) / 700F));
        float eyeOpen = smooth((tod - 16000F) / 300F) * (1F - smooth((tod - 21700F) / 300F));

        // fruncir: abierto -> se entrecierra -> sostiene -> relaja -> abierto
        float c = time % SQUINT_PERIOD;
        float squint;
        if (c < 60F) squint = 0F;
        else if (c < 84F) squint = smooth((c - 60F) / 24F);
        else if (c < 150F) squint = 1F;
        else if (c < 186F) squint = 1F - smooth((c - 150F) / 36F);
        else squint = 0F;
        return new Stage(crack, open, eyeOpen, squint);
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (ClientMoonState.visual() != MoonType.MOONLESS) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;

        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = ClientMoonState.intensity(pt);
        if (k <= 0F) return;
        float time = level.getGameTime() + pt;
        float tod = (level.getDayTime() % 24000L) + pt;
        Stage st = stage(tod, time);
        if (st.crack() <= 0F) return;

        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());
        Eye eye = new Eye(EYE_THETA);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();

        drawRiftInterior(mv, eye, st, k, time);

        float eyeAlpha = k * smooth((st.open() - 0.45F) / 0.3F);
        if (eyeAlpha > 0F) {
            float aTop = st.eyeOpen() * (1F - 0.45F * st.squint());
            float aBot = st.eyeOpen() * (1F - 0.30F * st.squint());
            drawLidRim(mv, eye, eyeAlpha);
            drawLidFlesh(mv, eye, eyeAlpha, aTop, aBot);
            if (aTop + aBot > 0.02F) {
                drawIris(mv, eye, eyeAlpha, aTop, aBot);
                drawPupil(mv, eye, eyeAlpha, time, st.squint(), aTop, aBot);
                if (aTop > 0.45F) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    drawGlint(mv, eye, eyeAlpha * smooth((aTop - 0.45F) / 0.2F));
                    RenderSystem.defaultBlendFunc();
                }
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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

    /** Media apertura de un labio (side = +1 / -1) con la grieta totalmente abierta. */
    private static float halfWidth(int i, int side) {
        float th = theta(i);
        float base = (float) Math.pow(Math.max(0F, 1F - (th / THETA_MAX) * (th / THETA_MAX)), 0.85);
        float seed = side > 0 ? 3.1F : 11.7F;
        float n = 0.75F * smoothNoise(i / 5F, seed) + 0.25F * hash(i * 2.3F + seed);
        return RIFT_W * base * (0.78F + 0.44F * n);
    }

    /** Factor de apertura del segmento i según la etapa: 0 si la ruptura todavía no llegó. */
    private static float widthFactor(int i, Stage st) {
        float reach = st.crack() * THETA_MAX;
        float tip = Mth.clamp((reach - Math.abs(theta(i))) / 6F, 0F, 1F);
        return tip * (0.03F + 0.97F * st.open());
    }

    private static Vector3f riftPoint(float thetaDeg, float phiDeg) {
        float th = thetaDeg * Mth.DEG_TO_RAD, ph = phiDeg * Mth.DEG_TO_RAD;
        float c = Mth.cos(ph);
        return new Vector3f(Mth.sin(th) * c * R, Mth.cos(th) * c * R, Mth.sin(ph) * R);
    }

    private static void drawRiftInterior(Matrix4f mv, Eye eye, Stage st, float k, float time) {
        // mientras está recién rota, el labio brilla más y titila
        float lipGain = 0.85F + 0.9F * (1F - st.open()) * (0.8F + 0.2F * Mth.sin(time * 0.7F));
        float eyeGain = 0.25F + 0.75F * st.eyeOpen();
        BufferBuilder bb = begin();
        for (int i = 0; i < SEG; i++) {
            float f0 = widthFactor(i, st), f1 = widthFactor(i + 1, st);
            if (f0 <= 0F && f1 <= 0F) continue;
            float t0 = theta(i), t1 = theta(i + 1);
            for (int j = 0; j < ACROSS; j++) {
                float a0 = -1F + 2F * j / ACROSS, a1 = -1F + 2F * (j + 1) / ACROSS;
                riftVertex(bb, mv, eye, t0, across(i, a0) * f0, Math.abs(a0), k, lipGain, eyeGain);
                riftVertex(bb, mv, eye, t1, across(i + 1, a0) * f1, Math.abs(a0), k, lipGain, eyeGain);
                riftVertex(bb, mv, eye, t1, across(i + 1, a1) * f1, Math.abs(a1), k, lipGain, eyeGain);
                riftVertex(bb, mv, eye, t0, across(i, a1) * f0, Math.abs(a1), k, lipGain, eyeGain);
            }
        }
        draw(bb);
    }

    private static float across(int i, float a) {
        return a * (a >= 0 ? halfWidth(i, 1) : halfWidth(i, -1));
    }

    private static void riftVertex(BufferBuilder bb, Matrix4f mv, Eye eye, float thetaDeg, float phiDeg,
                                   float edge, float k, float lipGain, float eyeGain) {
        Vector3f p = riftPoint(thetaDeg, phiDeg);
        float cos = Mth.clamp(p.dot(eye.center()) / R, -1F, 1F);
        float angle = (float) Math.toDegrees(Math.acos(cos));
        float eyeLight = 0.9F * eyeGain * (float) Math.exp(-(angle / 18F) * (angle / 18F));
        float lip = Math.max(0F, (edge - 0.55F) / 0.45F);
        float light = Math.min(1.3F, eyeLight + lipGain * lip * lip);
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

    /** Media altura del almendrado en s ∈ [-1, 1]. */
    private static float lid(float s) {
        return EYE_H * (float) Math.pow(Math.max(0F, 1F - s * s), 0.75);
    }

    /** Borde oscuro alrededor del almendrado. */
    private static void drawLidRim(Matrix4f mv, Eye eye, float a) {
        BufferBuilder bb = begin();
        int n = 48;
        for (int i = 0; i < n; i++) {
            float s0 = -1F + 2F * i / n, s1 = -1F + 2F * (i + 1) / n;
            for (int sg = -1; sg <= 1; sg += 2) {
                vertex(bb, mv, eye.point(EYE_L * s0, sg * lid(s0)), 0.04F, 0F, 0.07F, a);
                vertex(bb, mv, eye.point(EYE_L * s1, sg * lid(s1)), 0.04F, 0F, 0.07F, a);
                vertex(bb, mv, eye.point(EYE_L * s1, sg * (lid(s1) * 1.12F + 0.4F)), 0.04F, 0F, 0.07F, a);
                vertex(bb, mv, eye.point(EYE_L * s0, sg * (lid(s0) * 1.12F + 0.4F)), 0.04F, 0F, 0.07F, a);
            }
        }
        draw(bb);
    }

    /** Párpados: la parte del almendrado que no está abierta. Más oscuros en la línea de cierre. */
    private static void drawLidFlesh(Matrix4f mv, Eye eye, float a, float aTop, float aBot) {
        BufferBuilder bb = begin();
        int n = 48;
        for (int i = 0; i < n; i++) {
            float s0 = -1F + 2F * i / n, s1 = -1F + 2F * (i + 1) / n;
            float l0 = lid(s0), l1 = lid(s1);
            // superior: de la apertura (oscuro) al borde (tono piel del párpado)
            vertex(bb, mv, eye.point(EYE_L * s0, l0 * aTop), 0.015F, 0F, 0.025F, a);
            vertex(bb, mv, eye.point(EYE_L * s1, l1 * aTop), 0.015F, 0F, 0.025F, a);
            vertex(bb, mv, eye.point(EYE_L * s1, l1), 0.09F, 0.015F, 0.13F, a);
            vertex(bb, mv, eye.point(EYE_L * s0, l0), 0.09F, 0.015F, 0.13F, a);
            // inferior
            vertex(bb, mv, eye.point(EYE_L * s0, -l0 * aBot), 0.015F, 0F, 0.025F, a);
            vertex(bb, mv, eye.point(EYE_L * s1, -l1 * aBot), 0.015F, 0F, 0.025F, a);
            vertex(bb, mv, eye.point(EYE_L * s1, -l1), 0.07F, 0.01F, 0.1F, a);
            vertex(bb, mv, eye.point(EYE_L * s0, -l0), 0.07F, 0.01F, 0.1F, a);
        }
        draw(bb);
    }

    /** Iris visible solo entre los párpados. */
    private static void drawIris(Matrix4f mv, Eye eye, float a, float aTop, float aBot) {
        BufferBuilder bb = begin();
        int ns = 48, nt = 16;
        for (int i = 0; i < ns; i++) {
            float s0 = -1F + 2F * i / ns, s1 = -1F + 2F * (i + 1) / ns;
            for (int j = 0; j < nt; j++) {
                float t0 = -1F + 2F * j / nt, t1 = -1F + 2F * (j + 1) / nt;
                irisVertex(bb, mv, eye, EYE_L * s0, aperture(s0, t0, aTop, aBot), t0, a);
                irisVertex(bb, mv, eye, EYE_L * s1, aperture(s1, t0, aTop, aBot), t0, a);
                irisVertex(bb, mv, eye, EYE_L * s1, aperture(s1, t1, aTop, aBot), t1, a);
                irisVertex(bb, mv, eye, EYE_L * s0, aperture(s0, t1, aTop, aBot), t1, a);
            }
        }
        draw(bb);
    }

    /** t ∈ [-1, 1] mapeado entre el párpado inferior y el superior. */
    private static float aperture(float s, float t, float aTop, float aBot) {
        float l = lid(s);
        return Mth.lerp((t + 1F) * 0.5F, -l * aBot, l * aTop);
    }

    private static void irisVertex(BufferBuilder bb, Matrix4f mv, Eye eye, float u, float v, float t, float a) {
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
        // fibras radiales + sombra que proyectan los párpados
        float shade = (0.80F + 0.20F * Mth.sin(phi * 18F + d * 3F)) * (1F - 0.55F * t * t * t * t);
        vertex(bb, mv, eye.point(u, v), r * shade, g * shade, b * shade, a);
    }

    /** Pupila felina quieta en el centro; se contrae cuando el ojo frunce la mirada para enfocar. */
    private static void drawPupil(Matrix4f mv, Eye eye, float a, float time, float squint, float aTop, float aBot) {
        float width = 0.17F * EYE_H * (1F - 0.4F * squint) * (1F + 0.08F * Mth.sin(time * 0.02F));
        float top = 0.95F * lid(0F) * aTop, bottom = 0.95F * lid(0F) * aBot;
        BufferBuilder bb = begin();
        int n = 24;
        for (int j = 0; j < n; j++) {
            float t0 = -1F + 2F * j / n, t1 = -1F + 2F * (j + 1) / n;
            float w0 = width * (float) Math.pow(Math.max(0F, 1F - t0 * t0), 0.9);
            float w1 = width * (float) Math.pow(Math.max(0F, 1F - t1 * t1), 0.9);
            float v0 = Mth.lerp((t0 + 1F) * 0.5F, -bottom, top), v1 = Mth.lerp((t1 + 1F) * 0.5F, -bottom, top);
            vertex(bb, mv, eye.point(-w0, v0), 0F, 0F, 0F, a);
            vertex(bb, mv, eye.point(w0, v0), 0F, 0F, 0F, a);
            vertex(bb, mv, eye.point(w1, v1), 0F, 0F, 0F, a);
            vertex(bb, mv, eye.point(-w1, v1), 0F, 0F, 0F, a);
        }
        draw(bb);
    }

    private static void drawGlint(Matrix4f mv, Eye eye, float a) {
        BufferBuilder bb = begin();
        float gx = -0.32F * EYE_H, gy = 0.3F * EYE_H, gr = 0.12F * EYE_H;
        int n = 20;
        Vector3f c = eye.point(gx, gy);
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            vertex(bb, mv, c, 1F, 0.92F, 1F, 0.45F * a);
            vertex(bb, mv, c, 1F, 0.92F, 1F, 0.45F * a);
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

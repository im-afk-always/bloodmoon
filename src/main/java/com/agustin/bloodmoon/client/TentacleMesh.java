package com.agustin.bloodmoon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Tentáculo como una cadena de anillos octogonales que se afinan hacia la punta. Cada segmento rota
 * según {@link Bend} (radianes en X y Z), así ondula, se enrosca o azota. +Y local = a lo largo del tentáculo.
 * UV: u alrededor (0-1), v a lo largo (0-1).
 */
public final class TentacleMesh {
    private static final int SIDES = 8;

    @FunctionalInterface
    public interface Bend {
        /** Curvatura del segmento i (0..segs-1): {rotX, rotZ} en radianes. */
        float[] at(int i, float along);
    }

    private TentacleMesh() {}

    public static void render(PoseStack ps, VertexConsumer vc, int light, int overlay, float length, float baseRadius, int segs,
                              Bend bend, float r, float g, float b, float a) {
        float segLen = length / segs;
        Vector3f[][] ring = new Vector3f[segs + 1][SIDES + 1];
        Vector3f[][] norm = new Vector3f[segs + 1][SIDES + 1];
        ps.pushPose();
        for (int i = 0; i <= segs; i++) {
            float along = i / (float) segs;
            if (i > 0) {
                float[] rot = bend.at(i - 1, along);
                ps.mulPose(Axis.XP.rotation(rot[0]));
                ps.mulPose(Axis.ZP.rotation(rot[1]));
            }
            float rad = baseRadius * (1F - 0.88F * along) * (0.92F + 0.08F * Mth.sin(i * 1.7F));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            for (int k = 0; k <= SIDES; k++) {
                float ang = Mth.TWO_PI * k / SIDES;
                float cx = Mth.cos(ang), cz = Mth.sin(ang);
                ring[i][k] = m.transformPosition(cx * rad, 0F, cz * rad, new Vector3f());
                norm[i][k] = n.transform(new Vector3f(cx, 0.15F, cz)).normalize();
            }
            if (i < segs) ps.translate(0F, segLen, 0F);
        }
        ps.popPose();
        for (int i = 0; i < segs; i++) {
            float v0 = i / (float) segs, v1 = (i + 1) / (float) segs;
            for (int k = 0; k < SIDES; k++) {
                float u0 = k / (float) SIDES, u1 = (k + 1) / (float) SIDES;
                put(vc, ring[i][k], norm[i][k], u0, v0, light, overlay, r, g, b, a);
                put(vc, ring[i + 1][k], norm[i + 1][k], u0, v1, light, overlay, r, g, b, a);
                put(vc, ring[i + 1][k + 1], norm[i + 1][k + 1], u1, v1, light, overlay, r, g, b, a);
                put(vc, ring[i][k + 1], norm[i][k + 1], u1, v0, light, overlay, r, g, b, a);
            }
        }
    }

    private static void put(VertexConsumer vc, Vector3f p, Vector3f n, float u, float v, int light, int overlay,
                            float r, float g, float b, float a) {
        vc.addVertex(p.x(), p.y(), p.z()).setColor(r, g, b, a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(n.x(), n.y(), n.z());
    }
}

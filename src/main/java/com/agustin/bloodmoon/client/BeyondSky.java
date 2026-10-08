package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Cielo del Más Allá de la Grieta: púrpura, con nubes de nebulosa que se arremolinan, la Grieta abierta de lado
 * a lado y ojos enormes que vagan lentamente por la cúpula, parpadean y te miran.
 */
public class BeyondSky extends DimensionSpecialEffects {
    private static final ResourceLocation SKY_EYE = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/sky_eye.png");
    private static final float[][] EYES = new float[18][6];
    private static final float[][] RIFT = new float[40][2];

    static {
        RandomSource r = RandomSource.create(13013L);
        for (float[] e : EYES) {
            e[0] = r.nextFloat() * Mth.TWO_PI;          // azimut base
            e[1] = 0.12F + r.nextFloat() * 0.6F;          // elevación base
            e[2] = 5F + r.nextFloat() * 10F;              // tamaño
            e[3] = (r.nextFloat() - 0.5F) * 0.0012F;      // deriva
            e[4] = r.nextFloat() * 600F;                  // fase
            e[5] = r.nextFloat() * 10F;
        }
        float x = -1F;
        for (int i = 0; i < RIFT.length; i++) {
            RIFT[i][0] = x;
            RIFT[i][1] = (r.nextFloat() - 0.5F) * 0.08F;
            x += 2F / (RIFT.length - 1);
        }
    }

    public BeyondSky() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f m, Camera camera, Matrix4f projection,
                             boolean isFoggy, Runnable setupFog) {
        float time = ticks + partialTick;
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // cúpula púrpura con nebulosa
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 20, lon = 40;
        for (int i = 0; i < lat; i++) {
            float e0 = -0.3F + 1.3F * i / lat, e1 = -0.3F + 1.3F * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float a0 = Mth.TWO_PI * j / lon, a1 = Mth.TWO_PI * (j + 1) / lon;
                dome(bb, m, e0, a0, time);
                dome(bb, m, e0, a1, time);
                dome(bb, m, e1, a1, time);
                dome(bb, m, e1, a0, time);
            }
        }
        draw(bb);

        // la Grieta: una herida luminosa que cruza el cenit
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < RIFT.length - 1; i++) {
            float x0 = RIFT[i][0] * 80, z0 = RIFT[i][1] * 80 + 10, x1 = RIFT[i + 1][0] * 80, z1 = RIFT[i + 1][1] * 80 + 10;
            float w0 = 1.6F * (1F - Math.abs(RIFT[i][0])) + 0.2F, w1 = 1.6F * (1F - Math.abs(RIFT[i + 1][0])) + 0.2F;
            float y0 = 60 - Math.abs(RIFT[i][0]) * 25, y1 = 60 - Math.abs(RIFT[i + 1][0]) * 25;
            float pulse = 0.75F + 0.25F * Mth.sin(time * 0.05F + i * 0.4F);
            for (int layer = 0; layer < 2; layer++) {
                float w = layer == 0 ? 4F : 1F;
                float a = (layer == 0 ? 0.25F : 0.9F) * pulse;
                bb.addVertex(m, x0, y0, z0 - w0 * w).setColor(0.9F, 0.4F, 1F, 0F);
                bb.addVertex(m, x0, y0, z0).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x1, y1, z1).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x1, y1, z1 - w1 * w).setColor(0.9F, 0.4F, 1F, 0F);
                bb.addVertex(m, x0, y0, z0).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x0, y0, z0 + w0 * w).setColor(0.9F, 0.4F, 1F, 0F);
                bb.addVertex(m, x1, y1, z1 + w1 * w).setColor(0.9F, 0.4F, 1F, 0F);
                bb.addVertex(m, x1, y1, z1).setColor(1F, 0.85F, 1F, a);
            }
        }
        draw(bb);

        // ojos que vagan
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SKY_EYE);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] e : EYES) {
            float az = e[0] + time * e[3] + 0.35F * Mth.sin(time * 0.0009F + e[5]);
            float el = Mth.clamp(e[1] + 0.12F * Mth.sin(time * 0.0013F + e[5] * 2), 0.06F, 0.9F);
            float k = Mth.cos(el * Mth.HALF_PI);
            float dx = k * Mth.cos(az), dy = Mth.sin(el * Mth.HALF_PI), dz = k * Mth.sin(az);
            float cyc = (time + e[4]) % 600F;
            float open = cyc < 500 ? VoidEyeRenderer.smooth(cyc / 40F) : cyc < 520 ? 1F - VoidEyeRenderer.smooth((cyc - 500) / 12F)
                    : cyc < 560 ? 0F : VoidEyeRenderer.smooth((cyc - 560) / 20F);
            if (open < 0.03F) continue;
            float x = dx * 90, y = dy * 90, z = dz * 90;
            float ux = -dz, uz = dx;
            float ul = Mth.sqrt(ux * ux + uz * uz) + 1e-4F;
            ux = ux / ul * e[2]; uz = uz / ul * e[2];
            float vx = -dy * dx, vy = dx * dx + dz * dz, vz = -dy * dz;
            float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4F;
            float hgt = e[2] * 0.55F * open;
            vx = vx / vl * hgt; vy = vy / vl * hgt; vz = vz / vl * hgt;
            bb.addVertex(m, x - ux - vx, y - vy, z - uz - vz).setUv(0, 1).setColor(1F, 0.9F, 1F, 1F);
            bb.addVertex(m, x + ux - vx, y - vy, z + uz - vz).setUv(1, 1).setColor(1F, 0.9F, 1F, 1F);
            bb.addVertex(m, x + ux + vx, y + vy, z + uz + vz).setUv(1, 0).setColor(1F, 0.9F, 1F, 1F);
            bb.addVertex(m, x - ux + vx, y + vy, z - uz + vz).setUv(0, 0).setColor(1F, 0.9F, 1F, 1F);
        }
        draw(bb);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        return true;
    }

    private static void dome(BufferBuilder bb, Matrix4f m, float e, float a, float time) {
        float y = Mth.clamp(e, -1F, 1F);
        float k = Mth.sqrt(Math.max(0F, 1 - y * y));
        float x = k * Mth.cos(a), z = k * Mth.sin(a);
        float up = Mth.clamp(y, 0F, 1F);
        float h = (float) Math.pow(1 - up, 2.2);
        float r = Mth.lerp(h, 0.12F, 0.42F), g = Mth.lerp(h, 0.02F, 0.11F), b = Mth.lerp(h, 0.2F, 0.5F);
        if (y < 0) { r *= 0.55F; g *= 0.55F; b *= 0.6F; }
        float swirl = Mth.sin(a * 3 + y * 6 + time * 0.0015F) * Mth.sin(a * 7 - y * 4 - time * 0.001F);
        float neb = Math.max(0F, swirl) * up * 0.6F;
        r += 0.16F * neb; g += 0.03F * neb; b += 0.2F * neb;
        bb.addVertex(m, x * 100, y * 100, z * 100).setColor(r, g, b, 1F);
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}

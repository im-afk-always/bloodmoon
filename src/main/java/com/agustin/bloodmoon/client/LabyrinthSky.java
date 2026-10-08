package com.agustin.bloodmoon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Cielo del Laberinto del Vacío: cúpula casi negra que se aclara a un púrpura tenue en el horizonte,
 * una nebulosa violeta muy débil que cruza el cielo y estrellas mortecinas que titilan. Sin nubes ni sol.
 * Niebla espesa (como el Nether) para que los pasillos se pierdan en la oscuridad.
 */
public class LabyrinthSky extends DimensionSpecialEffects {
    private static final float[][] STARS = new float[500][5];

    static {
        RandomSource r = RandomSource.create(4815162342L);
        for (float[] s : STARS) {
            double u = r.nextDouble() * 2 - 1, t = r.nextDouble() * Math.PI * 2;
            double k = Math.sqrt(1 - u * u);
            s[0] = (float) (k * Math.cos(t)); s[1] = (float) Math.abs(u); s[2] = (float) (k * Math.sin(t));
            s[3] = 0.15F + r.nextFloat() * 0.35F;   // tamaño
            s[4] = r.nextFloat() * 100F;            // fase
        }
    }

    public LabyrinthSky() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        float time = ticks + partialTick;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lat = 18, lon = 36;
        float R = 100F;
        for (int i = 0; i < lat; i++) {
            float e0 = -0.35F + 1.35F * i / lat, e1 = -0.35F + 1.35F * (i + 1) / lat;   // elevación en "seno"
            for (int j = 0; j < lon; j++) {
                float a0 = Mth.TWO_PI * j / lon, a1 = Mth.TWO_PI * (j + 1) / lon;
                skyVertex(bb, modelViewMatrix, R, e0, a0, time);
                skyVertex(bb, modelViewMatrix, R, e0, a1, time);
                skyVertex(bb, modelViewMatrix, R, e1, a1, time);
                skyVertex(bb, modelViewMatrix, R, e1, a0, time);
            }
        }
        draw(bb);

        // estrellas tenues (aditivas)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : STARS) {
            float tw = 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(time * 0.05F + s[4]));
            float a = tw * 0.55F * Mth.clamp(s[1] * 4F, 0F, 1F);
            float x = s[0] * 95, y = s[1] * 95, z = s[2] * 95;
            // cuadrado perpendicular a la dirección
            float ux = -s[2], uz = s[0];
            float ul = Mth.sqrt(ux * ux + uz * uz) + 1e-4F;
            ux = ux / ul * s[3]; uz = uz / ul * s[3];
            float vy = s[3];
            bb.addVertex(modelViewMatrix, x - ux, y - vy, z - uz).setColor(0.85F, 0.7F, 1F, a);
            bb.addVertex(modelViewMatrix, x + ux, y - vy, z + uz).setColor(0.85F, 0.7F, 1F, a);
            bb.addVertex(modelViewMatrix, x + ux, y + vy, z + uz).setColor(0.85F, 0.7F, 1F, a);
            bb.addVertex(modelViewMatrix, x - ux, y + vy, z - uz).setColor(0.85F, 0.7F, 1F, a);
        }
        draw(bb);

        EyeFightFx.renderSky(modelViewMatrix, time, partialTick);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        return true;
    }

    private static void skyVertex(BufferBuilder bb, Matrix4f m, float R, float e, float a, float time) {
        float y = Mth.clamp(e, -1F, 1F);
        float k = Mth.sqrt(Math.max(0F, 1 - y * y));
        float x = k * Mth.cos(a), z = k * Mth.sin(a);
        // degradé: horizonte púrpura tenue -> cenit casi negro
        float up = Mth.clamp(y, 0F, 1F);
        float h = (float) Math.pow(1 - up, 3.0);
        float r = Mth.lerp(h, 0.012F, 0.15F), g = Mth.lerp(h, 0.0F, 0.04F), b = Mth.lerp(h, 0.03F, 0.24F);
        if (y < 0) { r *= 0.6F; g *= 0.6F; b *= 0.7F; }
        // nebulosa: franja inclinada con estructura
        float band = x * 0.55F + y * 0.75F - z * 0.36F;
        float neb = (float) Math.exp(-band * band * 9) * (0.55F + 0.45F * Mth.sin(a * 5 + y * 7 + time * 0.0007F))
                * (0.6F + 0.4F * Mth.sin(a * 11 - y * 13));
        neb = Math.max(0F, neb) * up;
        r += 0.11F * neb; g += 0.025F * neb; b += 0.16F * neb;
        bb.addVertex(m, x * R, y * R, z * R).setColor(r, g, b, 1F);
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}

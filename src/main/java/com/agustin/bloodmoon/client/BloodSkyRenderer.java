package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cielo de la Luna de la Cosecha: una luna realista (mares, cráteres, oscurecimiento del borde) teñida de carmesí, con
 * un resplandor suave y un único halo tenue; y una capa de nubes suaves (en vez de las cúbicas
 * vanilla) que se curva hacia el horizonte, deriva con el viento y se enciende de rojo cerca de la luna.
 * Se dibuja después del cielo vanilla y antes del terreno.
 */
public final class BloodSkyRenderer {
    private static final ResourceLocation MOON = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/environment/blood_moon.png");
    private static final ResourceLocation CLOUDS = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/environment/blood_clouds.png");

    /** Domo de nubes: alto en el cenit, radio horizontal, celdas de la grilla, repeticiones de la textura. */
    private static final float CLOUD_H = 70F, CLOUD_E = 380F, CLOUD_TILES = 1.4F;
    private static final int CLOUD_GRID = 44;

    /** Nubes procedurales (shader propio); si no cargó, se usa la textura en mosaico como respaldo. */
    private static net.minecraft.client.renderer.ShaderInstance cloudShader;

    private BloodSkyRenderer() {}

    public static void onRegisterShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        try {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "harvest_clouds"), DefaultVertexFormat.POSITION_TEX_COLOR),
                    s -> cloudShader = s);
        } catch (Exception e) {
            BloodMoonMod.LOGGER.error("Harvest Moon cloud shader could not load; using the texture clouds", e);
            cloudShader = null;
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** Las nubes vanilla se ocultan mientras dibujamos las propias. */
    public static boolean hidesVanillaClouds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return false;
        return ClientMoonState.visual().customSky() && ClientMoonState.intensity(1F) > 0.35F;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        MoonType type = ClientMoonState.visual();
        if (!type.customSky()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = ClientMoonState.intensity(pt);
        if (k <= 0.004F) return;
        float time = level.getGameTime() + pt;
        float clear = 1F - level.getRainLevel(pt);

        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());
        float tod = level.getTimeOfDay(pt);
        // mismo marco que la luna vanilla: está en y = -100 de este sistema
        Matrix4f celestial = new Matrix4f(mv).rotateY(-90F * Mth.DEG_TO_RAD).rotateX(tod * 360F * Mth.DEG_TO_RAD);
        Vector3f moonDir = new Matrix4f().rotateY(-90F * Mth.DEG_TO_RAD).rotateX(tod * 360F * Mth.DEG_TO_RAD)
                .transformDirection(new Vector3f(0F, -1F, 0F)).normalize();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float mk = k * (0.35F + 0.65F * clear);
        drawMoon(celestial, type, mk, time);
        drawClouds(mv, moonDir, k * (0.55F + 0.45F * clear), time, mc.gameRenderer.getMainCamera().getPosition());

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ luna

    private static void drawMoon(Matrix4f m, MoonType type, float k, float time) {
        float R = type.moonSize;
        float y = -100F;
        float breathe = 1F + 0.04F * Mth.sin(time * 0.02F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        // resplandor suave que tiñe el cielo alrededor
        fan(m, y, R * 6F * breathe, 0.95F, 0.12F, 0.07F, 0.3F * k);
        fan(m, y, R * 2.2F, 1F, 0.16F, 0.1F, 0.3F * k);
        // un único halo, tenue
        ring(m, y, R * 3.6F, R * 3.95F, R * 4.4F, 1F, 0.26F, 0.16F, 0.15F * type.halo * k);

        // el disco: textura realista teñida (mezcla normal: tapa el cielo de atrás)
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        Minecraft.getInstance().getTextureManager().getTexture(MOON).setFilter(true, false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float cr = 1F, cg = 0.24F, cb = 0.17F;
        bb.addVertex(m, -R, y, -R).setUv(0F, 0F).setColor(cr, cg, cb, k);
        bb.addVertex(m, R, y, -R).setUv(1F, 0F).setColor(cr, cg, cb, k);
        bb.addVertex(m, R, y, R).setUv(1F, 1F).setColor(cr, cg, cb, k);
        bb.addVertex(m, -R, y, R).setUv(0F, 1F).setColor(cr, cg, cb, k);
        draw(bb);
        // brillo propio encima del disco (aditivo): la luna "arde"
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float g = 0.4F * k;
        bb.addVertex(m, -R, y, -R).setUv(0F, 0F).setColor(1F, 0.35F, 0.25F, g);
        bb.addVertex(m, R, y, -R).setUv(1F, 0F).setColor(1F, 0.35F, 0.25F, g);
        bb.addVertex(m, R, y, R).setUv(1F, 1F).setColor(1F, 0.35F, 0.25F, g);
        bb.addVertex(m, -R, y, R).setUv(0F, 1F).setColor(1F, 0.35F, 0.25F, g);
        draw(bb);
        RenderSystem.defaultBlendFunc();
    }

    /** Disco radial sobre el plano y: color pleno en el centro y transparente en el borde. */
    private static void fan(Matrix4f m, float y, float r, float cr, float cg, float cb, float a) {
        if (a <= 0.003F) return;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 72;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            bb.addVertex(m, 0F, y, 0F).setColor(cr, cg, cb, a);
            bb.addVertex(m, Mth.cos(a0) * r, y, Mth.sin(a0) * r).setColor(cr, cg, cb, 0F);
            bb.addVertex(m, Mth.cos(a1) * r, y, Mth.sin(a1) * r).setColor(cr, cg, cb, 0F);
        }
        draw(bb);
    }

    /** Anillo suave: transparente en r0 y r2, intensidad plena en r1. */
    private static void ring(Matrix4f m, float y, float r0, float r1, float r2, float cr, float cg, float cb, float a) {
        if (a <= 0.003F) return;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 128;
        float[][] rings = {{r0, 0F}, {r1, a}, {r2, 0F}};
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int j = 0; j < 2; j++) {
                float ri = rings[j][0], ai = rings[j][1], ro = rings[j + 1][0], ao = rings[j + 1][1];
                bb.addVertex(m, c0 * ri, y, s0 * ri).setColor(cr, cg, cb, ai);
                bb.addVertex(m, c0 * ro, y, s0 * ro).setColor(cr, cg, cb, ao);
                bb.addVertex(m, c1 * ro, y, s1 * ro).setColor(cr, cg, cb, ao);
                bb.addVertex(m, c0 * ri, y, s0 * ri).setColor(cr, cg, cb, ai);
                bb.addVertex(m, c1 * ro, y, s1 * ro).setColor(cr, cg, cb, ao);
                bb.addVertex(m, c1 * ri, y, s1 * ri).setColor(cr, cg, cb, ai);
            }
        }
        draw(bb);
    }

    // ------------------------------------------------------------------ nubes

    private static void drawClouds(Matrix4f mv, Vector3f moonDir, float k, float time, Vec3 cam) {
        if (k <= 0.004F) return;
        RenderSystem.defaultBlendFunc();
        net.minecraft.client.renderer.ShaderInstance proc = cloudShader;
        float su, sv, tiles;
        if (proc != null) {
            // nubes generadas por píxel: no se repiten, cambian de forma y el viento las arrastra
            RenderSystem.setShader(() -> proc);
            float ct = (float) (Minecraft.getInstance().level.getGameTime() % 2_000_000L) + (time - (float) Math.floor(time));
            proc.safeGetUniform("CloudTime").set(ct);
            proc.safeGetUniform("CloudOffset").set((float) (cam.x / 3000.0), (float) (cam.z / 3000.0));
            float ml = Mth.sqrt(moonDir.x() * moonDir.x() + moonDir.z() * moonDir.z()) + 1e-4F;
            proc.safeGetUniform("MoonUV").set(moonDir.x() / ml, moonDir.z() / ml);
            su = 0F;
            sv = 0F;
            tiles = 1F;
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, CLOUDS);
            Minecraft.getInstance().getTextureManager().getTexture(CLOUDS).setFilter(true, false);
            // viento + un leve desplazamiento con el jugador (paralaje)
            su = (float) (time * 0.000075 + cam.x / 9000.0);
            sv = (float) (time * 0.00003 + cam.z / 9000.0);
            tiles = CLOUD_TILES;
        }
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int n = CLOUD_GRID;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                cloudVertex(bb, mv, moonDir, i, j, n, su, sv, tiles, k);
                cloudVertex(bb, mv, moonDir, i + 1, j, n, su, sv, tiles, k);
                cloudVertex(bb, mv, moonDir, i + 1, j + 1, n, su, sv, tiles, k);
                cloudVertex(bb, mv, moonDir, i, j + 1, n, su, sv, tiles, k);
            }
        }
        draw(bb);
    }

    private static void cloudVertex(BufferBuilder bb, Matrix4f mv, Vector3f moonDir, int i, int j, int n, float su, float sv, float tiles, float k) {
        float fx = 2F * i / n - 1F, fz = 2F * j / n - 1F;
        float x = fx * CLOUD_E, z = fz * CLOUD_E;
        float rr = Math.min(1.2F, Mth.sqrt(fx * fx + fz * fz));
        float y = CLOUD_H * (1F - 1.12F * rr * rr);                       // se curva hasta perderse bajo el horizonte
        float fade = 1F - smooth((rr - 0.55F) / 0.42F);
        // iluminación: cerca de la luna se encienden; lejos, casi negras contra el cielo
        float len = Mth.sqrt(x * x + y * y + z * z);
        float d = Math.max(0F, (x * moonDir.x() + y * moonDir.y() + z * moonDir.z()) / len);
        float lit = d * d * d * d;
        float glow = (float) Math.pow(d, 18);
        float r = Math.min(1F, 0.5F + 0.45F * lit + 0.35F * glow);
        float g = Math.min(1F, 0.06F + 0.12F * lit + 0.22F * glow);
        float b = Math.min(1F, 0.05F + 0.08F * lit + 0.15F * glow);
        float sc = 0.25F;   // misma dirección, más cerca: no lo recorta el plano lejano con poca distancia de render
        bb.addVertex(mv, x * sc, y * sc, z * sc).setUv(fx * tiles + su, fz * tiles + sv).setColor(r, g, b, 0.93F * k * fade);
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}

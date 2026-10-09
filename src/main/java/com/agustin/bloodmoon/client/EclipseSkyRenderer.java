package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientEclipse;
import com.agustin.bloodmoon.Eclipse;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Eclipse Solar: un sol realista (oscurecimiento del borde, granulación, resplandor) y la luna nueva que lo cruza.
 * La luna se pinta del color del cielo, así que es invisible salvo donde tapa al sol, como en la realidad. Al
 * quedar una astilla aparecen las cuentas de Baily y estalla el anillo de diamante (un destello con rayos larguísimos);
 * en la totalidad, la corona perlada con sus serpentinas y protuberancias rosadas.
 */
public final class EclipseSkyRenderer {
    private static final ResourceLocation SUN = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/environment/eclipse_sun.png");
    private static final ResourceLocation CORONA = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/environment/eclipse_corona.png");
    /** Radio del disco solar en unidades del cielo (a 100 de distancia): más grande que el real, para que impresione. */
    private static final float SR = 8F;
    private static final float Y = 100F;

    private static float lastDiamond, lastU = -9F;
    private static boolean wasTotal;

    private EclipseSkyRenderer() {}

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** El sol realista reemplaza al vanilla durante todo el día del eclipse. */
    public static boolean hidesVanillaSun() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD && ClientEclipse.isEclipseDay(mc.level.getDayTime());
    }

    /** Estado del eclipse ahora (null si no hay eclipse hoy o no estamos en el Overworld). */
    public static Eclipse.State state(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return null;
        return ClientEclipse.state(mc.level.getDayTime(), pt);
    }

    private static Vector3f sunDir(ClientLevel level, float pt) {
        float tod = level.getTimeOfDay(pt);
        return new Matrix4f().rotateY(-90F * Mth.DEG_TO_RAD).rotateX(tod * 360F * Mth.DEG_TO_RAD)
                .transformDirection(new Vector3f(0F, 1F, 0F)).normalize();
    }

    // ------------------------------------------------------------------ cielo

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!hidesVanillaSun()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float clear = 1F - level.getRainLevel(pt);
        if (clear <= 0.01F) return;
        Eclipse.State st = ClientEclipse.state(level.getDayTime(), pt);
        float time = level.getGameTime() + pt;

        float tod = level.getTimeOfDay(pt);
        Matrix4f m = new Matrix4f(event.getModelViewMatrix()).rotateY(-90F * Mth.DEG_TO_RAD).rotateX(tod * 360F * Mth.DEG_TO_RAD);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        float b = st == null ? 1F : st.brightness();
        float s = st == null ? 9F : st.s();
        float d = st == null ? 9F : st.d();
        float mx = s * Eclipse.DIR_X * SR, mz = s * Eclipse.DIR_Y * SR;

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        // 1) el disco solar
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SUN);
        Minecraft.getInstance().getTextureManager().getTexture(SUN).setFilter(true, false);
        quad(m, 0F, 0F, SR, 1F, 1F, 1F, clear);

        // 2) la corona (detrás de la luna: la luna tapa su centro)
        float cor = st == null ? 0F : st.corona();
        if (cor > 0.003F) {
            RenderSystem.setShaderTexture(0, CORONA);
            Minecraft.getInstance().getTextureManager().getTexture(CORONA).setFilter(true, false);
            float breathe = 1F + 0.02F * Mth.sin(time * 0.03F);
            quad(m, 0F, 0F, SR * 4.5F * breathe, 1F, 1F, 1F, clear * cor);
        }

        // 3) la luna nueva, del color del cielo: solo se nota donde tapa al sol
        if (st != null && d < 2.3F) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Vec3 sky = level.getSkyColor(mc.gameRenderer.getMainCamera().getPosition(), pt);
            float dim = 0.8F;
            disc(m, mx, mz, SR * Eclipse.MOON_R, (float) sky.x * dim, (float) sky.y * dim, (float) sky.z * dim, 1F);
        }

        // 5) el resplandor del sol, encima de la luna: el brillo de lo que queda del disco la vuelve invisible
        //    contra el cielo (como en la realidad) y se apaga a medida que la luna lo tapa
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float glow = clear * (float) Math.pow(b, 1.3);
        fan(m, 0F, 0F, SR * 9F, 1F, 0.86F, 0.62F, 0.32F * glow);
        fan(m, 0F, 0F, SR * 3.2F, 1F, 0.95F, 0.82F, 0.55F * glow);

        // 6) cuentas de Baily y anillo de diamante, sobre el borde por donde se va (o vuelve) el último rayo
        if (st != null) {
            float side = s < 0F ? 1F : -1F;
            float px = side * Eclipse.DIR_X * SR, pz = side * Eclipse.DIR_Y * SR;
            float baseAng = (float) Math.atan2(pz, px);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            float beads = st.beads() * clear;
            if (beads > 0.01F) {
                for (int i = 0; i < 6; i++) {
                    float a = baseAng + (i - 2.5F) * 0.13F + 0.03F * Mth.sin(i * 7.1F);
                    float flick = 0.55F + 0.45F * Mth.sin(time * (0.9F + i * 0.37F) + i * 2F);
                    float bx = Mth.cos(a) * SR * 1.005F, bz = Mth.sin(a) * SR * 1.005F;
                    fan(m, bx, bz, SR * (0.18F + 0.1F * flick), 1F, 0.97F, 0.9F, beads * flick);
                }
            }
            float di = st.diamond() * clear;
            if (di > 0.01F) {
                // el diamante: un núcleo cegador y un halo amplio
                fan(m, px, pz, SR * 3.4F * di, 1F, 0.96F, 0.86F, 0.9F * di);
                fan(m, px, pz, SR * 0.9F, 1F, 1F, 1F, di);
                // rayos: una cruz larguísima a lo largo del borde y rayos más cortos alrededor
                float tang = baseAng + Mth.HALF_PI;
                ray(m, px, pz, tang, SR * (10F + 16F * di), SR * 0.07F, 0.95F * di);
                ray(m, px, pz, tang + Mth.PI, SR * (10F + 16F * di), SR * 0.07F, 0.95F * di);
                ray(m, px, pz, baseAng, SR * (6F + 9F * di), SR * 0.06F, 0.8F * di);
                ray(m, px, pz, baseAng + Mth.PI, SR * (3F + 4F * di), SR * 0.05F, 0.5F * di);
                for (int i = 0; i < 8; i++) {
                    float a = baseAng + Mth.PI / 8F + i * Mth.PI / 4F + 0.05F * Mth.sin(time * 0.2F + i);
                    ray(m, px, pz, a, SR * (2.5F + 4F * di) * (0.7F + 0.3F * ((i * 5) % 3)), SR * 0.035F, 0.55F * di);
                }
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(Matrix4f m, float cx, float cz, float r, float cr, float cg, float cb, float a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(m, cx - r, Y, cz - r).setUv(0F, 0F).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx + r, Y, cz - r).setUv(1F, 0F).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx + r, Y, cz + r).setUv(1F, 1F).setColor(cr, cg, cb, a);
        bb.addVertex(m, cx - r, Y, cz + r).setUv(0F, 1F).setColor(cr, cg, cb, a);
        draw(bb);
    }

    /** Disco radial (pleno en el centro, transparente en el borde). */
    private static void fan(Matrix4f m, float cx, float cz, float r, float cr, float cg, float cb, float a) {
        if (a <= 0.003F || r <= 0.01F) return;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            bb.addVertex(m, cx, Y, cz).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + Mth.cos(a0) * r, Y, cz + Mth.sin(a0) * r).setColor(cr, cg, cb, 0F);
            bb.addVertex(m, cx + Mth.cos(a1) * r, Y, cz + Mth.sin(a1) * r).setColor(cr, cg, cb, 0F);
        }
        draw(bb);
    }

    /** Disco liso (con un borde suave de un 2%). */
    private static void disc(Matrix4f m, float cx, float cz, float r, float cr, float cg, float cb, float a) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 96;
        float ri = r * 0.985F;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            bb.addVertex(m, cx, Y, cz).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + c0 * ri, Y, cz + s0 * ri).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + c1 * ri, Y, cz + s1 * ri).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + c0 * ri, Y, cz + s0 * ri).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + c0 * r, Y, cz + s0 * r).setColor(cr, cg, cb, 0F);
            bb.addVertex(m, cx + c1 * r, Y, cz + s1 * r).setColor(cr, cg, cb, 0F);
            bb.addVertex(m, cx + c0 * ri, Y, cz + s0 * ri).setColor(cr, cg, cb, a);
            bb.addVertex(m, cx + c1 * r, Y, cz + s1 * r).setColor(cr, cg, cb, 0F);
            bb.addVertex(m, cx + c1 * ri, Y, cz + s1 * ri).setColor(cr, cg, cb, a);
        }
        draw(bb);
    }

    /** Rayo de luz que nace en (cx, cz) y se afina y apaga hacia la punta. */
    private static void ray(Matrix4f m, float cx, float cz, float ang, float len, float w, float a) {
        if (a <= 0.003F) return;
        float dx = Mth.cos(ang), dz = Mth.sin(ang), nx = -dz, nz = dx;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, cx + nx * w, Y, cz + nz * w).setColor(1F, 0.97F, 0.88F, a);
        bb.addVertex(m, cx - nx * w, Y, cz - nz * w).setColor(1F, 0.97F, 0.88F, a);
        bb.addVertex(m, cx + dx * len, Y, cz + dz * len).setColor(1F, 0.9F, 0.75F, 0F);
        draw(bb);
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // ------------------------------------------------------------------ destello en pantalla y sonidos

    /** Capa de interfaz: el anillo de diamante encandila si lo estás mirando. */
    public static void renderFlash(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        Eclipse.State st = state(pt);
        if (st == null || st.diamond() <= 0.02F) return;
        Vector3f sd = sunDir(mc.level, pt);
        Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
        float facing = Math.max(0F, sd.dot(look));
        float a = (float) Math.pow(st.diamond(), 3) * 0.45F * facing * facing * (1F - mc.level.getRainLevel(pt));
        if (a <= 0.004F) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (Math.min(1F, a) * 255) << 24) | 0xFFF4DC);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Eclipse.State st = state(0F);
        if (st == null) {
            lastDiamond = 0F;
            wasTotal = false;
            lastU = -9F;
            return;
        }
        float di = st.diamond();
        if (di > 0.6F && lastDiamond <= 0.6F) {                     // el último (o primer) rayo
            play(SoundEvents.BEACON_ACTIVATE, 0.7F, 1F);
            play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1F);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1F);
        }
        boolean total = st.totality();
        if (total && !wasTotal) {
            play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1F);
            play(SoundEvents.BELL_RESONATE, 0.5F, 0.8F);
        }
        if (!total && wasTotal) play(SoundEvents.BEACON_POWER_SELECT, 0.7F, 0.8F);
        // en la totalidad, un murmullo grave cada tanto
        if (total && mc.level.getGameTime() % 100 == 0) play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 0.5F);
        lastDiamond = di;
        wasTotal = total;
        lastU = st.u();
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }
}

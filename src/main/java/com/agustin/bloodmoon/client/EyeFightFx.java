package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.VoidEye;
import com.agustin.bloodmoon.network.EyeMadnessPayload;
import com.agustin.bloodmoon.network.EyeTitlePayload;
import com.agustin.bloodmoon.registry.ModSounds;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Efectos de cliente de la pelea contra el Observador:
 * - Locura: viñeta que se cierra, ojos que se abren en los bordes de la pantalla, susurros en la lengua de la
 *   Grieta, latidos, la cámara que se inclina y el campo de visión que "respira".
 * - Temblores (grito, onda, despertar, implosión) y niebla teñida según la fase.
 * - Títulos en el alfabeto de encantamientos que se fijan letra por letra (despertar y despedida).
 * - El cielo del Laberinto: tinte rojo-violeta en fase 2 y decenas de ojos que se abren en fase 3.
 */
public final class EyeFightFx {
    private static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");
    private static final ResourceLocation WATCHER = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/entity/void_eye/watcher.png");
    private static final RandomSource RNG = RandomSource.create();
    private static final float[][] SKY_EYES = new float[72][6];
    private static final float[][] EDGE_EYES = new float[14][4];

    static {
        RandomSource r = RandomSource.create(666L);
        for (float[] s : SKY_EYES) {
            double u = 0.12 + r.nextDouble() * 0.85, a = r.nextDouble() * Math.PI * 2, k = Math.sqrt(1 - u * u);
            s[0] = (float) (k * Math.cos(a)); s[1] = (float) u; s[2] = (float) (k * Math.sin(a));
            s[3] = 2.5F + r.nextFloat() * 6F;    // tamaño
            s[4] = r.nextFloat() * 400F;          // fase de parpadeo
            s[5] = r.nextFloat();                 // umbral de aparición
        }
        for (int i = 0; i < EDGE_EYES.length; i++) {
            float[] e = EDGE_EYES[i];
            float side = r.nextFloat();
            float along = r.nextFloat();
            // pegados a los bordes
            if (side < 0.25F) { e[0] = along; e[1] = 0.04F + r.nextFloat() * 0.12F; }
            else if (side < 0.5F) { e[0] = along; e[1] = 0.84F + r.nextFloat() * 0.12F; }
            else if (side < 0.75F) { e[0] = 0.03F + r.nextFloat() * 0.12F; e[1] = along; }
            else { e[0] = 0.85F + r.nextFloat() * 0.12F; e[1] = along; }
            e[2] = 22F + r.nextFloat() * 30F;     // tamaño px
            e[3] = r.nextFloat() * 300F;          // fase
        }
    }

    private static float madness, madnessO, madnessTarget;
    private static int lastPacket = -1000, clientTicks;
    private static int whisperTimer = 60, heartTimer = 0;
    private static float shake;
    private static float skyMix, skyMixO;
    private static int skyPhase = 1;
    private static boolean nearEye;
    private static int titleMode = -1;
    private static float titleAge;
    private static float flash, flashO;
    private static final String[] WHISPERS = new String[8];
    private static final float[][] WHISPER_POS = new float[5][4];

    private EyeFightFx() {}

    public static void init() {
        EyeMadnessPayload.handler = p -> Minecraft.getInstance().execute(() -> {
            madnessTarget = p.madness();
            lastPacket = clientTicks;
        });
        EyeTitlePayload.handler = p -> Minecraft.getInstance().execute(() -> {
            titleMode = p.mode();
            titleAge = 0;
            if (p.mode() == EyeTitlePayload.FAREWELL) {
                madnessTarget = 0;
                shake = 1.6F;
                play(SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 0.3F);
            }
        });
    }

    public static void reset() {
        madness = madnessO = madnessTarget = 0;
        titleMode = -1;
        shake = 0;
        skyMix = skyMixO = 0;
        nearEye = false;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    // ------------------------------------------------------------------ tick

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        clientTicks++;
        if (mc.level == null || mc.player == null) return;
        if (clientTicks - lastPacket > 40) madnessTarget = 0;
        madnessO = madness;
        madness += (madnessTarget - madness) * 0.25F;
        shake *= 0.9F;
        int titleLen = titleMode == EyeTitlePayload.AWAKEN ? 240 : titleMode == EyeTitlePayload.ASCEND ? 340 : 200;
        if (titleMode >= 0 && ++titleAge > titleLen) titleMode = -1;
        flashO = flash;
        flash *= 0.88F;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (en instanceof com.agustin.bloodmoon.entity.TitanTentacle tt && tt.distanceToSqr(mc.player) < 200 * 200) {
                if (tt.tickCount == com.agustin.bloodmoon.entity.TitanTentacle.SLAM) { shake = Math.max(shake, 2.2F); flash = Math.max(flash, 0.25F); }
                else if (tt.tickCount < com.agustin.bloodmoon.entity.TitanTentacle.EMERGE) shake = Math.max(shake, 0.35F);
            }
            if (en instanceof com.agustin.bloodmoon.entity.AbyssTear at && at.tickCount == at.flight() + com.agustin.bloodmoon.entity.AbyssTear.BOOM - 1) {
                double d = at.target().distanceTo(mc.player.position());
                if (d < 60) {
                    shake = Math.max(shake, (float) (1.3 * (1 - d / 60)));
                    flash = Math.max(flash, (float) (0.4 * (1 - d / 60)));
                }
            }
            if (en instanceof com.agustin.bloodmoon.entity.VoidFist vf && vf.tickCount == com.agustin.bloodmoon.entity.VoidFist.SLAM) {
                double d = vf.distanceTo(mc.player);
                if (d < 50) shake = Math.max(shake, (float) (1.6 * (1 - d / 50)));
            }
            if (en instanceof com.agustin.bloodmoon.entity.ColossalEye ce && ce.distanceToSqr(mc.player) < 320 * 320) {
                int a = ce.tickCount;
                if (a == com.agustin.bloodmoon.entity.ColossalEye.FIRE) { flash = 0.75F; shake = Math.max(shake, 2F); }
                if (ce.isFiring()) shake = Math.max(shake, 0.9F);
                else if (a >= com.agustin.bloodmoon.entity.ColossalEye.LOCK) shake = Math.max(shake, 0.25F);
                else shake = Math.max(shake, 0.08F);
            }
        }

        VoidEye eye = findEye(mc);
        nearEye = eye != null;
        skyMixO = skyMix;
        float wantSky = 0;
        if (eye != null) {
            skyPhase = eye.getPhase();
            wantSky = eye.isDeadOrDying() ? 0F : eye.getState() == VoidEye.S_AWAKEN ? 0.3F : 1F;
            float age = eye.clientStateAge(0F);
            switch (eye.getState()) {
                case VoidEye.S_SCREAM -> shake = Math.max(shake, 1.1F - age / 70F);
                case VoidEye.S_WAVE -> { if (age < 8) shake = Math.max(shake, 0.6F); }
                case VoidEye.S_PULL -> shake = Math.max(shake, 0.15F + 0.25F * age / VoidEye.PULL_TICKS);
                case VoidEye.S_AWAKEN -> shake = Math.max(shake, 0.6F * smooth((age - 50) / 100F) * (age < 165 ? 1F : 0F));
                case VoidEye.S_GAZE, VoidEye.S_SWEEP -> shake = Math.max(shake, 0.12F);
                case VoidEye.S_ASCEND -> shake = Math.max(shake, 0.3F + 0.9F * age / VoidEye.ASCEND_TICKS);
                case com.agustin.bloodmoon.entity.UnboundObserver.S_EMERGE -> shake = Math.max(shake, 0.5F * (age < 125 ? 1F : 0F));
                case com.agustin.bloodmoon.entity.UnboundObserver.S_MAW -> { if (age > com.agustin.bloodmoon.entity.UnboundObserver.MAW_OPEN) shake = Math.max(shake, 0.35F); }
                case com.agustin.bloodmoon.entity.UnboundObserver.S_TEARS, com.agustin.bloodmoon.entity.UnboundObserver.S_TITAN -> shake = Math.max(shake, 0.2F);
                default -> {}
            }
            if (eye.isDeadOrDying()) shake = Math.max(shake, 0.2F + 0.8F * eye.deathTime / (float) VoidEye.DEATH_TICKS);
        }
        skyMix += (wantSky - skyMix) * 0.02F;

        // susurros y latidos
        if (madness > 28 && --whisperTimer <= 0) {
            whisperTimer = 30 + RNG.nextInt((int) Math.max(20, 140 - madness));
            Vec3 p = mc.player.position().add(RNG.nextGaussian() * 3, 1 + RNG.nextGaussian(), RNG.nextGaussian() * 3);
            mc.level.playLocalSound(p.x, p.y, p.z, ModSounds.EYE_WHISPER.get(), SoundSource.HOSTILE,
                    0.25F + 0.75F * madness / 100F, 0.75F + RNG.nextFloat() * 0.4F, false);
            shuffleWhispers();
        }
        if (madness > 62 && --heartTimer <= 0) {
            heartTimer = (int) (34 - madness * 0.18F);
            play(SoundEvents.WARDEN_HEARTBEAT, 0.5F + 0.5F * madness / 100F, 0.9F);
        }
        if (WHISPERS[0] == null || clientTicks % 70 == 0) shuffleWhispers();
    }

    private static VoidEye findEye(Minecraft mc) {
        VoidEye best = null;
        double bestD = 220 * 220;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof VoidEye eye) {
                double d = eye.distanceToSqr(mc.player);
                if (d < bestD) {
                    bestD = d;
                    best = eye;
                }
            }
        }
        return best;
    }

    private static void shuffleWhispers() {
        for (int i = 0; i < WHISPERS.length; i++) WHISPERS[i] = Component.translatable("bloodmoon.eye.whisper." + i).getString();
        for (float[] w : WHISPER_POS) {
            w[0] = 0.1F + RNG.nextFloat() * 0.8F;
            w[1] = 0.12F + RNG.nextFloat() * 0.76F;
            w[2] = RNG.nextInt(WHISPERS.length);
            w[3] = RNG.nextFloat();
        }
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.HOSTILE, volume, pitch, false);
        }
    }

    // ------------------------------------------------------------------ cámara, FOV y niebla

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        float m = Mth.lerp(pt, madnessO, madness) / 100F;
        float t = clientTicks + pt;
        if (m > 0.01F) event.setRoll(event.getRoll() + Mth.sin(t * 0.07F) * 7F * m * m);
        if (shake > 0.01F) {
            event.setYaw(event.getYaw() + (RNG.nextFloat() - 0.5F) * shake * 2.4F);
            event.setPitch(event.getPitch() + (RNG.nextFloat() - 0.5F) * shake * 2.4F);
            event.setRoll(event.getRoll() + (RNG.nextFloat() - 0.5F) * shake * 1.5F);
        }
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        float pt = (float) event.getPartialTick();
        float m = Mth.lerp(pt, madnessO, madness) / 100F;
        if (m < 0.01F) return;
        float t = clientTicks + pt;
        event.setFOV(event.getFOV() * (1F + 0.09F * m * Mth.sin(t * 0.055F)));
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float k = Mth.lerp((float) event.getPartialTick(), skyMixO, skyMix);
        if (k <= 0.001F) return;
        float r = skyPhase == 1 ? 0.06F : skyPhase == 2 ? 0.12F : 0.2F;
        float g = 0.0F;
        float b = skyPhase == 1 ? 0.12F : skyPhase == 2 ? 0.08F : 0.1F;
        float dark = 1F - 0.5F * madness / 100F;
        event.setRed(Mth.lerp(k, event.getRed(), r) * dark);
        event.setGreen(Mth.lerp(k, event.getGreen(), g) * dark);
        event.setBlue(Mth.lerp(k, event.getBlue(), b) * dark);
    }

    // ------------------------------------------------------------------ cielo

    /** Lo llama el cielo del Laberinto: tinte y, en fase 3, ojos que se abren en la cúpula. */
    public static void renderSky(Matrix4f m, float time, float partialTick) {
        float k = Mth.lerp(partialTick, skyMixO, skyMix);
        if (k <= 0.002F) return;
        RenderSystem.enableBlend();
        if (skyPhase >= 2) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float a = k * (skyPhase == 3 ? 0.22F : 0.12F) * (0.85F + 0.15F * Mth.sin(time * 0.05F));
            int lon = 24;
            for (int j = 0; j < lon; j++) {
                float a0 = Mth.TWO_PI * j / lon, a1 = Mth.TWO_PI * (j + 1) / lon;
                for (int i = 0; i < 6; i++) {
                    float e0 = -0.2F + 1.2F * i / 6, e1 = -0.2F + 1.2F * (i + 1) / 6;
                    float w0 = 1F - Math.abs(e0 - 0.15F), w1 = 1F - Math.abs(e1 - 0.15F);
                    domeV(bb, m, e0, a0, 0.6F, 0.05F, 0.25F, a * w0);
                    domeV(bb, m, e0, a1, 0.6F, 0.05F, 0.25F, a * w0);
                    domeV(bb, m, e1, a1, 0.6F, 0.05F, 0.25F, a * w1);
                    domeV(bb, m, e1, a0, 0.6F, 0.05F, 0.25F, a * w1);
                }
            }
            draw(bb);
        }
        if (skyPhase >= 3) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, WATCHER);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (float[] s : SKY_EYES) {
                if (s[5] > k) continue;
                float cyc = (time + s[4]) % 400F;
                float open = cyc < 300 ? smooth(cyc / 30F) : cyc < 330 ? 1F - smooth((cyc - 300) / 15F) : 0F;
                if (open <= 0.02F) continue;
                float x = s[0] * 90, y = s[1] * 90, z = s[2] * 90;
                float ux = -s[2], uz = s[0];
                float ul = Mth.sqrt(ux * ux + uz * uz) + 1e-4F;
                ux = ux / ul * s[3]; uz = uz / ul * s[3];
                // "arriba" del ojo: perpendicular a la dirección y a u
                float vx = -s[1] * s[0], vy = s[0] * s[0] + s[2] * s[2], vz = -s[1] * s[2];
                float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4F;
                float hgt = s[3] * open;
                vx = vx / vl * hgt; vy = vy / vl * hgt; vz = vz / vl * hgt;
                float a = Math.min(1F, k * 1.2F);
                bb.addVertex(m, x - ux - vx, y - vy, z - uz - vz).setUv(0, 1).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x + ux - vx, y - vy, z + uz - vz).setUv(1, 1).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x + ux + vx, y + vy, z + uz + vz).setUv(1, 0).setColor(1F, 0.85F, 1F, a);
                bb.addVertex(m, x - ux + vx, y + vy, z - uz + vz).setUv(0, 0).setColor(1F, 0.85F, 1F, a);
            }
            draw(bb);
        }
        RenderSystem.defaultBlendFunc();
    }

    private static void domeV(BufferBuilder bb, Matrix4f m, float e, float a, float r, float g, float b, float alpha) {
        float y = Mth.clamp(e, -1F, 1F);
        float k = Mth.sqrt(Math.max(0F, 1 - y * y));
        bb.addVertex(m, k * Mth.cos(a) * 95, y * 95, k * Mth.sin(a) * 95).setColor(r, g, b, Math.max(0F, alpha));
    }

    // ------------------------------------------------------------------ superposición: locura

    public static void renderMadness(GuiGraphics g, DeltaTracker delta) {
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float m = Mth.lerp(pt, madnessO, madness) / 100F;
        if (m < 0.01F || Minecraft.getInstance().options.hideGui && m < 0.3F) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = clientTicks + pt;
        g.flush();
        float beat = madness > 62 ? Math.max(0F, Mth.sin(t * 0.35F)) * 0.15F : 0F;
        vignette(g, w, h, smooth(m * 1.25F) * 0.92F + beat, 1F - 0.62F * smooth(m));

        // ojos que se abren en los bordes
        if (m > 0.42F) {
            int count = (int) (EDGE_EYES.length * smooth((m - 0.42F) / 0.5F));
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (int i = 0; i < count; i++) {
                float[] e = EDGE_EYES[i];
                float cyc = (t + e[3]) % 120F;
                float open = cyc < 90 ? smooth(cyc / 12F) : 1F - smooth((cyc - 90) / 8F);
                if (open < 0.05F) continue;
                int size = (int) e[2];
                int eh = Math.max(1, (int) (size * open));
                int x = (int) (e[0] * w) - size / 2, y = (int) (e[1] * h) - eh / 2;
                g.setColor(1F, 0.85F, 1F, Math.min(1F, (m - 0.42F) * 3F) * 0.85F);
                RenderSystem.enableBlend();
                g.blit(WATCHER, x, y, size, eh, 0, 0, 32, 32, 32, 32);
            }
            g.setColor(1F, 1F, 1F, 1F);
        }

        // susurros en la lengua de la Grieta
        if (m > 0.55F && WHISPERS[0] != null) {
            Font font = Minecraft.getInstance().font;
            float readable = smooth((m - 0.6F) / 0.4F);
            int n = 2 + (int) (3 * smooth((m - 0.55F) / 0.45F));
            for (int i = 0; i < n; i++) {
                float[] p = WHISPER_POS[i];
                String s = WHISPERS[(int) p[2]];
                if (s == null) continue;
                float flick = 0.5F + 0.5F * Mth.sin(t * (0.3F + p[3]) + i * 2);
                int alpha = (int) (Mth.clamp(flick * (m - 0.55F) * 3F, 0F, 1F) * 200);
                if (alpha < 6) continue;
                int x = (int) (p[0] * w), y = (int) (p[1] * h);
                int dx = 0;
                for (int c = 0; c < s.length(); c++) {
                    char ch = s.charAt(c);
                    boolean lock = ((c * 7 + i * 3) % 10) / 10F < readable;
                    Component comp = lock || ch == ' ' ? Component.literal(String.valueOf(ch))
                            : Component.literal(String.valueOf((char) ('a' + RNG.nextInt(26)))).withStyle(Style.EMPTY.withFont(ALT_FONT));
                    int col = lock ? 0xE0A0FF : 0x8A2BE2;
                    g.drawString(font, comp, x + dx, y, (alpha << 24) | col, false);
                    dx += font.width(comp) + 1;
                }
            }
        }
    }

    /** Viñeta: transparente en un óvalo central de radio relativo inner, oscura hacia los bordes. */
    private static void vignette(GuiGraphics g, int w, int h, float alpha, float inner) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float cx = w / 2F, cy = h / 2F, rx = w * 0.72F, ry = h * 0.72F;
        int seg = 64;
        float a = Mth.clamp(alpha, 0F, 1F);
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float ix0 = cx + Mth.cos(a0) * rx * inner, iy0 = cy + Mth.sin(a0) * ry * inner;
            float ix1 = cx + Mth.cos(a1) * rx * inner, iy1 = cy + Mth.sin(a1) * ry * inner;
            float ox0 = cx + Mth.cos(a0) * rx * 1.6F, oy0 = cy + Mth.sin(a0) * ry * 1.6F;
            float ox1 = cx + Mth.cos(a1) * rx * 1.6F, oy1 = cy + Mth.sin(a1) * ry * 1.6F;
            bb.addVertex(m, ix0, iy0, 0).setColor(0.05F, 0F, 0.08F, 0F);
            bb.addVertex(m, ox0, oy0, 0).setColor(0.03F, 0F, 0.05F, a);
            bb.addVertex(m, ox1, oy1, 0).setColor(0.03F, 0F, 0.05F, a);
            bb.addVertex(m, ix0, iy0, 0).setColor(0.05F, 0F, 0.08F, 0F);
            bb.addVertex(m, ox1, oy1, 0).setColor(0.03F, 0F, 0.05F, a);
            bb.addVertex(m, ix1, iy1, 0).setColor(0.05F, 0F, 0.08F, 0F);
        }
        draw(bb);
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ superposición: títulos

    public static void renderTitle(GuiGraphics g, DeltaTracker delta) {
        float fl = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), flashO, flash);
        if (fl > 0.01F) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (Math.min(1F, fl) * 255) << 24) | 0xFFF0FF);
            g.flush();
        }
        if (titleMode < 0) return;
        float a = titleAge + delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        g.flush();
        if (titleMode == EyeTitlePayload.ASCEND) {
            // la pupila se abre como una grieta: blanco total, el viaje, y el nombre de la forma final
            float white = smooth((a - 55) / 40F) * (1F - smooth((a - 125) / 55F));
            if (white > 0.004F) g.fill(0, 0, w, h, ((int) (white * 255) << 24) | 0xFBF4FF);
            g.flush();
            float fade = smooth((a - 150) / 15F) * (1F - smooth((a - 300) / 35F));
            band(g, w, h, (int) (h * 0.22F), (int) (h * 0.16F), fade * 0.55F);
            String title = Component.translatable("bloodmoon.eye.title2").getString();
            String sub = Component.translatable("bloodmoon.eye.subtitle2").getString();
            glyphLine(g, title, w / 2F, h * 0.24F, 2.6F, a, 160, 2.5F, fade, 0xFF8AD8);
            glyphLine(g, sub, w / 2F, h * 0.24F + 30, 1.3F, a, 160 + title.length() * 2.5F + 10, 1.5F, fade, 0xB07CE8);
            return;
        }
        if (titleMode == EyeTitlePayload.AWAKEN) {
            float fade = smooth(a / 20F) * (1F - smooth((a - 205) / 30F));
            band(g, w, h, (int) (h * 0.22F), (int) (h * 0.16F), fade * 0.55F);
            String title = Component.translatable("bloodmoon.eye.title").getString();
            String sub = Component.translatable("bloodmoon.eye.subtitle").getString();
            glyphLine(g, title, w / 2F, h * 0.24F, 3.2F, a, 30, 3F, fade, 0xD8A8FF);
            glyphLine(g, sub, w / 2F, h * 0.24F + 34, 1.3F, a, 30 + title.length() * 3 + 10, 1.5F, fade, 0xB07CE8);
        } else {
            // destello blanco, negrura total y la despedida
            float white = 1F - smooth((a - 3) / 10F);
            float black = smooth(a / 6F) * (1F - smooth((a - 150) / 40F));
            if (black > 0.004F) g.fill(0, 0, w, h, ((int) (black * 255) << 24));
            g.flush();
            if (white > 0.004F) g.fill(0, 0, w, h, ((int) (white * 255) << 24) | 0xFFFFFF);
            g.flush();
            String text = Component.translatable("bloodmoon.eye.farewell").getString();
            float textFade = smooth((a - 25) / 10F) * (1F - smooth((a - 135) / 20F));
            glyphLine(g, text, w / 2F, h * 0.46F, 2.4F, a, 32, 2.5F, textFade, 0xE6CCFF);
        }
    }

    private static void band(GuiGraphics g, int w, int h, int cy, int half, float alpha) {
        if (alpha <= 0.004F) return;
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float top = cy - half, mid = cy + half * 0.3F, bot = cy + half * 2;
        bb.addVertex(m, 0, top, 0).setColor(0F, 0F, 0F, 0F);
        bb.addVertex(m, 0, mid, 0).setColor(0.02F, 0F, 0.04F, alpha);
        bb.addVertex(m, w, mid, 0).setColor(0.02F, 0F, 0.04F, alpha);
        bb.addVertex(m, w, top, 0).setColor(0F, 0F, 0F, 0F);
        bb.addVertex(m, 0, mid, 0).setColor(0.02F, 0F, 0.04F, alpha);
        bb.addVertex(m, 0, bot, 0).setColor(0F, 0F, 0F, 0F);
        bb.addVertex(m, w, bot, 0).setColor(0F, 0F, 0F, 0F);
        bb.addVertex(m, w, mid, 0).setColor(0.02F, 0F, 0.04F, alpha);
        draw(bb);
    }

    /** Texto centrado cuyas letras giran entre runas y se fijan una por una desde lockStart, cada step ticks. */
    private static void glyphLine(GuiGraphics g, String text, float cx, float y, float scale, float time, float lockStart, float step,
                                  float alpha, int color) {
        if (alpha <= 0.02F) return;
        Font font = Minecraft.getInstance().font;
        int[] widths = new int[text.length()];
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            widths[i] = font.width(String.valueOf(text.charAt(i))) + 1;
            total += widths[i];
        }
        g.pose().pushPose();
        g.pose().translate(cx - total * scale / 2F, y, 0);
        g.pose().scale(scale, scale, 1F);
        int a = Math.max(4, (int) (alpha * 255));
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != ' ') {
                float lockAt = lockStart + i * step;
                Component c;
                int col;
                if (time >= lockAt) {
                    float since = Math.min(1F, (time - lockAt) / 6F);
                    c = Component.literal(String.valueOf(ch));
                    int r = (int) Mth.lerp(since, 255, color >> 16 & 255), gg = (int) Mth.lerp(since, 255, color >> 8 & 255),
                            b = (int) Mth.lerp(since, 255, color & 255);
                    col = (a << 24) | (r << 16) | (gg << 8) | b;
                } else {
                    c = Component.literal(String.valueOf((char) ('a' + RNG.nextInt(26)))).withStyle(Style.EMPTY.withFont(ALT_FONT));
                    int f = 120 + RNG.nextInt(80);
                    col = (a << 24) | (f / 2 << 16) | (f / 6 << 8) | f;
                }
                int gw = font.width(c);
                g.drawString(font, c, x + (widths[i] - gw) / 2, 0, col, true);
            }
            x += widths[i];
        }
        g.pose().popPose();
    }

    private static void draw(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}

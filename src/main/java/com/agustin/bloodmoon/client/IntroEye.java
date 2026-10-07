package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.network.IntroPayload;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * La visión del ojo al entrar por primera vez a un mundo: la pantalla se oscurece, un ojo púrpura de pupila
 * felina se abre frente al jugador y encima aparece un texto en el alfabeto de la mesa de encantamientos;
 * cada letra cambia de símbolo a toda velocidad hasta fijarse, una por una, en el alfabeto normal.
 * Después el ojo se cierra y la visión se desvanece. /bloodmoon intro la repite.
 */
public final class IntroEye {
    private static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");
    private static final int DARK_IN = 30, OPEN_START = 25, OPEN_END = 50, TEXT_START = 60, FIRST_LOCK = 26, LOCK_STEP = 5,
            HOLD = 60, CLOSE_LEN = 22, FADE_OUT = 25;

    private static int pending = -1;     // ticks de espera antes de empezar (mundo cargando)
    private static float age = -1;
    private static String text = "";
    private static int[] glyphs = new int[0];
    private static int lockedCount;
    private static final RandomSource RNG = RandomSource.create();

    private IntroEye() {}

    public static void init() {
        IntroPayload.handler = () -> Minecraft.getInstance().execute(() -> pending = 50);
    }

    public static void reset() {
        pending = -1;
        age = -1;
    }

    private static int lockTime(int i) {
        return TEXT_START + FIRST_LOCK + i * LOCK_STEP;
    }

    private static int closeStart() {
        return lockTime(Math.max(0, text.length() - 1)) + HOLD;
    }

    private static int end() {
        return closeStart() + CLOSE_LEN + FADE_OUT;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (pending >= 0) {
            if (mc.player == null || mc.level == null || mc.screen != null) return;   // esperar a que se vea el mundo
            if (--pending > 0) return;
            pending = -1;
            age = 0;
            text = Component.translatable("bloodmoon.intro.text").getString();
            glyphs = new int[text.length()];
            lockedCount = 0;
            play(SoundEvents.AMBIENT_CAVE.value(), 1F, 0.5F);
            play(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.5F);
            return;
        }
        if (age < 0) return;
        age++;
        if (age == OPEN_START) play(SoundEvents.ENDER_EYE_LAUNCH, 0.8F, 0.4F);
        // símbolos que cambian rápido; las letras se fijan de a una
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = RNG.nextInt(26);
        int locked = 0;
        for (int i = 0; i < text.length(); i++) if (age >= lockTime(i)) locked = i + 1;
        while (lockedCount < locked) {
            if (text.charAt(lockedCount) != ' ') play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.25F, 1.6F + RNG.nextFloat() * 0.3F);
            lockedCount++;
        }
        if (age == closeStart()) play(SoundEvents.ENDERMAN_TELEPORT, 0.7F, 0.4F);
        if (age > end()) age = -1;
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.AMBIENT, volume, pitch, false);
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    // ------------------------------------------------------------------ dibujo

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (age < 0) return;
        float a = age + delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float cs = closeStart();
        float dark = smooth(a / DARK_IN) * (1F - smooth((a - cs - CLOSE_LEN) / FADE_OUT));
        float open = smooth((a - OPEN_START) / (OPEN_END - OPEN_START)) * (1F - smooth((a - cs) / CLOSE_LEN));
        float textAlpha = smooth((a - TEXT_START) / 10F) * (1F - smooth((a - cs) / (CLOSE_LEN * 0.8F)));

        g.fill(0, 0, w, h, ((int) (dark * 0.82F * 255) << 24) | 0x05000A);
        g.flush();

        float ew = Math.min(w * 0.36F, h * 0.62F), eh = ew * 0.42F;     // ojo pequeño, centrado
        float cx = w / 2F, cy = h * 0.58F;
        if (open > 0.002F || dark > 0.01F) drawEye(g, cx, cy, ew, eh, open, a, dark);

        if (textAlpha > 0.02F) drawText(g, cx, cy - eh * 0.75F - 18, a, textAlpha);
    }

    private static void drawEye(GuiGraphics g, float cx, float cy, float ew, float eh, float open, float time, float dark) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();

        // resplandor púrpura detrás del ojo (aditivo)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float glow = (0.25F + 0.45F * open) * dark * (0.9F + 0.1F * Mth.sin(time * 0.2F));
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float rx = ew * 0.95F, ry = eh * 1.6F;
            bb.addVertex(m, cx, cy, 0).setColor(0.55F, 0.15F, 0.85F, glow);
            bb.addVertex(m, cx + Mth.cos(a0) * rx, cy + Mth.sin(a0) * ry, 0).setColor(0.3F, 0.0F, 0.5F, 0F);
            bb.addVertex(m, cx + Mth.cos(a1) * rx, cy + Mth.sin(a1) * ry, 0).setColor(0.3F, 0.0F, 0.5F, 0F);
        }
        draw(bb);
        RenderSystem.defaultBlendFunc();
        if (open <= 0.002F) {
            RenderSystem.enableDepthTest();
            return;
        }

        // malla del ojo recortada por los párpados (almendra)
        int cols = 96, rows = 56;
        float hw = ew / 2F;
        float pupilShift = Mth.sin(time * 0.03F) * 0.05F;
        float dilate = 1F + 0.12F * Mth.sin(time * 0.07F);
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < cols; i++) {
            float u0 = -1F + 2F * i / cols, u1 = -1F + 2F * (i + 1) / cols;
            float l0 = lid(u0) * open, l1 = lid(u1) * open;
            for (int j = 0; j < rows; j++) {
                float v0 = -1F + 2F * j / rows, v1 = -1F + 2F * (j + 1) / rows;
                float[] c00 = eyeColor(u0, v0 * l0, pupilShift, dilate, time), c10 = eyeColor(u1, v0 * l1, pupilShift, dilate, time);
                float[] c11 = eyeColor(u1, v1 * l1, pupilShift, dilate, time), c01 = eyeColor(u0, v1 * l0, pupilShift, dilate, time);
                bb.addVertex(m, cx + u0 * hw, cy + v0 * l0 * eh / 2F, 0).setColor(c00[0], c00[1], c00[2], dark);
                bb.addVertex(m, cx + u0 * hw, cy + v1 * l0 * eh / 2F, 0).setColor(c01[0], c01[1], c01[2], dark);
                bb.addVertex(m, cx + u1 * hw, cy + v1 * l1 * eh / 2F, 0).setColor(c11[0], c11[1], c11[2], dark);
                bb.addVertex(m, cx + u1 * hw, cy + v0 * l1 * eh / 2F, 0).setColor(c10[0], c10[1], c10[2], dark);
            }
        }
        draw(bb);

        // borde de los párpados
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float t = Math.max(2F, eh * 0.06F);
        for (int i = 0; i < cols; i++) {
            float u0 = -1F + 2F * i / cols, u1 = -1F + 2F * (i + 1) / cols;
            float y0 = lid(u0) * open * eh / 2F, y1 = lid(u1) * open * eh / 2F;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                bb.addVertex(m, cx + u0 * hw, cy + sgn * y0, 0).setColor(0.16F, 0.02F, 0.24F, dark);
                bb.addVertex(m, cx + u0 * hw, cy + sgn * (y0 + t), 0).setColor(0.04F, 0F, 0.08F, 0F);
                bb.addVertex(m, cx + u1 * hw, cy + sgn * (y1 + t), 0).setColor(0.04F, 0F, 0.08F, 0F);
                bb.addVertex(m, cx + u1 * hw, cy + sgn * y1, 0).setColor(0.16F, 0.02F, 0.24F, dark);
            }
        }
        draw(bb);
        RenderSystem.enableDepthTest();
    }

    /** Media altura del ojo (0..1) en la posición horizontal u ∈ [-1, 1]: almendra con puntas. */
    private static float lid(float u) {
        return (float) Math.pow(Math.max(0F, 1F - u * u), 0.85F);
    }

    /** Color en coordenadas del ojo: u horizontal, v vertical (ambas en [-1, 1] dentro del ojo abierto). */
    private static float[] eyeColor(float u, float v, float shift, float dilate, float time) {
        float uu = (u - shift) * 2.38F, vv = v;                             // espacio del iris (redondo en pantalla)
        float rIris = (float) Math.sqrt(uu * uu + vv * vv) / 0.82F;
        float r, gc, b;
        if (rIris < 1F) {
            float rings = 0.85F + 0.15F * Mth.sin(rIris * 22F + time * 0.05F);
            float k = 1F - rIris;
            r = (0.45F + 0.5F * k) * rings;
            gc = (0.06F + 0.35F * k * k) * rings;
            b = (0.7F + 0.3F * k) * rings;
            if (rIris > 0.9F) { r *= 0.4F; gc *= 0.3F; b *= 0.5F; }        // anillo exterior oscuro
            // pupila vertical (felina)
            float pw = 0.17F * dilate, ph = 0.74F;
            float p = (uu * uu) / (pw * pw) + (vv * vv) / (ph * ph);
            if (p < 1F) { r = 0.02F; gc = 0F; b = 0.03F; }
            else if (p < 1.5F) { float e = (p - 1F) / 0.5F; r *= e; gc *= e; b *= e; }
            // brillo
            float gx = uu + 0.3F, gy = vv + 0.38F;
            if (gx * gx + gy * gy < 0.012F) { r = 1F; gc = 0.92F; b = 1F; }
        } else {
            // esclerótica oscura con venas púrpuras
            float vein = Mth.abs(Mth.sin(u * 11F + v * 5F) * Mth.cos(v * 9F - u * 3F));
            float base = 0.07F + 0.05F * (1F - Math.abs(u));
            r = base + (vein > 0.92F ? 0.12F : 0F);
            gc = 0.01F;
            b = base * 1.6F + (vein > 0.92F ? 0.16F : 0F);
            float edge = Math.min(1F, (1F - Math.abs(v)) * 3F);
            r *= 0.4F + 0.6F * edge; b *= 0.4F + 0.6F * edge;
        }
        return new float[]{r, gc, b};
    }

    private static void drawText(GuiGraphics g, float cx, float y, float time, float alpha) {
        Font font = Minecraft.getInstance().font;
        float scale = 2F;
        int total = 0;
        int[] widths = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            widths[i] = font.width(String.valueOf(text.charAt(i))) + 1;
            total += widths[i];
        }
        g.pose().pushPose();
        g.pose().translate(cx - total * scale / 2F, y, 0);
        g.pose().scale(scale, scale, 1F);
        int x = 0;
        int a = Math.max(4, (int) (alpha * 255));
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != ' ') {
                boolean locked = time >= lockTime(i);
                Component c;
                int color;
                if (locked) {
                    float since = Math.min(1F, (time - lockTime(i)) / 8F);
                    c = Component.literal(String.valueOf(ch));
                    int rr = (int) Mth.lerp(since, 255, 225), gg = (int) Mth.lerp(since, 255, 190), bb = 255;
                    color = (a << 24) | (rr << 16) | (gg << 8) | bb;
                } else {
                    char glyph = (char) ('a' + (i < glyphs.length ? glyphs[i] : 0));
                    c = Component.literal(String.valueOf(glyph)).withStyle(Style.EMPTY.withFont(ALT_FONT));
                    int flick = 150 + RNG.nextInt(70);
                    color = (a << 24) | (flick / 2 << 16) | (flick / 5 << 8) | flick;
                }
                int gw = font.width(c);
                g.drawString(font, c, x + (widths[i] - gw) / 2, 0, color, true);
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

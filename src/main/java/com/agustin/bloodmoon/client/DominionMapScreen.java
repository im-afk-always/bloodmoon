package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientDominion;
import com.agustin.bloodmoon.invasion.InvasionRank;
import com.agustin.bloodmoon.network.DominionMapPayload;
import com.agustin.bloodmoon.network.DominionMapRequestPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Mapa del Dominio: el terreno que el jugador ya vio y, encima, los Dominios del Vacío completos aunque no estén
 * cargados (los manda el servidor). Arrastrar para mover, rueda para zoom. El botón del ojo abre la jerarquía.
 */
public class DominionMapScreen extends Screen {
    private static final ResourceLocation EYE = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/gui/eye_button.png");
    private static final int[] ZOOMS = {1, 2, 4, 8, 16};
    private static int savedZoom = 2;

    private final Screen parent;
    private double viewX, viewZ;
    private int zoom = savedZoom;
    private DynamicTexture tex;
    private ResourceLocation texId;
    private int tw, th, step, originX, originZ;
    private boolean dirty = true, showHierarchy, centered;
    private int seenVersion = -1, ticks;
    private int mapL, mapT, mapW, mapH;

    public DominionMapScreen(Screen parent) {
        super(Component.translatable("bloodmoon.map.title"));
        this.parent = parent;
    }

    private int bpp() {
        return ZOOMS[zoom];
    }

    @Override
    protected void init() {
        mapL = 10;
        mapT = 30;
        mapW = width - 20;
        mapH = height - 44;
        if (!centered && minecraft.player != null) {   // se abre centrado en el jugador
            viewX = minecraft.player.getX();
            viewZ = minecraft.player.getZ();
            centered = true;
        }
        dirty = true;
        PacketDistributor.sendToServer(DominionMapRequestPayload.INSTANCE);
    }

    @Override
    public void tick() {
        if (++ticks % 100 == 0) PacketDistributor.sendToServer(DominionMapRequestPayload.INSTANCE);
        if (ClientDominion.version != seenVersion) dirty = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        savedZoom = zoom;
        if (texId != null) minecraft.getTextureManager().release(texId);
        texId = null;
        tex = null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A080C);
    }

    // ------------------------------------------------------------------ imagen del mapa

    private void rebuild() {
        int bpp = bpp();
        step = Math.max(4, bpp);
        double bx0 = viewX - mapW / 2.0 * bpp, bz0 = viewZ - mapH / 2.0 * bpp;
        originX = Math.floorDiv((int) Math.floor(bx0), step) * step;
        originZ = Math.floorDiv((int) Math.floor(bz0), step) * step;
        int w = (int) Math.ceil((mapW * bpp + (bx0 - originX)) / step) + 1;
        int h = (int) Math.ceil((mapH * bpp + (bz0 - originZ)) / step) + 1;
        if (tex == null || w != tw || h != th) {
            if (texId != null) minecraft.getTextureManager().release(texId);
            tw = w;
            th = h;
            tex = new DynamicTexture(new NativeImage(tw, th, true));
            texId = minecraft.getTextureManager().register("bloodmoon_dominion_map", tex);
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        List<DominionMapPayload.FactionView> fs = ClientDominion.factions;
        for (int j = 0; j < th; j++) {
            for (int i = 0; i < tw; i++) {
                int bx = originX + i * step + step / 2, bz = originZ + j * step + step / 2;
                int rgb = terrain(bx, bz);
                for (DominionMapPayload.FactionView f : fs) rgb = overlay(f, bx, bz, rgb);
                img.setPixelRGBA(i, j, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
            }
        }
        tex.upload();
        dirty = false;
        seenVersion = ClientDominion.version;
    }

    private static int at(int bx, int bz) {
        return ExploredMap.sample(ChunkPos.asLong(bx >> 4, bz >> 4), (bx & 15) >> 2, (bz & 15) >> 2);
    }

    /** Color del terreno explorado con relieve (o niebla si no se vio). */
    private int terrain(int bx, int bz) {
        int v = at(bx, bz);
        if (v == 0) {
            int n = ((bx >> 4) * 31 + (bz >> 4) * 17) & 3;
            return 0x15131A + n * 0x020202;
        }
        int rgb = v & 0xFFFFFF;
        int north = at(bx, bz - step);
        float shade = 1F;
        if (north != 0) {
            int h = v >>> 24, hn = north >>> 24;
            shade = h > hn ? 1.1F : h < hn ? 0.82F : 0.96F;
        }
        return scale(rgb, shade);
    }

    private int overlay(DominionMapPayload.FactionView f, int bx, int bz, int rgb) {
        int val = f.at(bx >> 4, bz >> 4);
        boolean healing = !f.active();
        if (val > 0) {
            if (val < 50) rgb = mix(rgb, healing ? 0x5A9A6A : 0x9B4DFF, 0.16F + 0.22F * val / 50F);
            else if (val < 100) rgb = mix(rgb, healing ? 0x3A6A4A : 0x6A2A9E, 0.5F);
            else rgb = mix(rgb, healing ? 0x203A28 : 0x1A0C22, 0.72F);
        }
        double dx = bx - f.centerX(), dz = bz - f.centerZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(d - f.radius()) < step * 0.75) rgb = mix(rgb, 0xC070FF, 0.85F);
        return rgb;
    }

    private static int scale(int rgb, float k) {
        int r = Mth.clamp((int) ((rgb >> 16 & 0xFF) * k), 0, 255);
        int g = Mth.clamp((int) ((rgb >> 8 & 0xFF) * k), 0, 255);
        int b = Mth.clamp((int) ((rgb & 0xFF) * k), 0, 255);
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ dibujo

    private int sx(double bx) {
        return (int) Math.round(mapL + mapW / 2.0 + (bx - viewX) / bpp());
    }

    private int sz(double bz) {
        return (int) Math.round(mapT + mapH / 2.0 + (bz - viewZ) / bpp());
    }

    private DominionMapPayload.FactionView selected() {
        DominionMapPayload.FactionView best = null;
        double bd = Double.MAX_VALUE;
        for (DominionMapPayload.FactionView f : ClientDominion.factions) {
            double dx = f.centerX() - viewX, dz = f.centerZ() - viewZ;
            double d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    private int eyeX() { return mapL + mapW - 24; }

    private int eyeY() { return mapT + 4; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (dirty) rebuild();
        // marco
        g.fill(mapL - 2, mapT - 2, mapL + mapW + 2, mapT + mapH + 2, 0xFF2A1A36);
        g.enableScissor(mapL, mapT, mapL + mapW, mapT + mapH);
        if (texId != null) {
            int x = sx(originX), y = sz(originZ);
            int w = (int) Math.round((double) tw * step / bpp()), h = (int) Math.round((double) th * step / bpp());
            g.blit(texId, x, y, w, h, 0F, 0F, tw, th, tw, th);
        }
        drawIcons(g);
        g.disableScissor();

        // encabezado
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), mapL, 6, 0xFFE0D0F0, true);
        DominionMapPayload.FactionView f = selected();
        Component info = f == null
                ? Component.translatable("bloodmoon.map.none")
                : Component.translatable("bloodmoon.map.info", f.name(),
                        f.active() ? Component.translatable("bloodmoon.invasion.phase." + f.phase()) : Component.translatable("bloodmoon.map.healing"),
                        f.essence(), f.deadChunks(), f.obelisks());
        g.drawString(font, info, mapL, 17, f == null ? 0xFF8A8090 : 0xFFC9A6F0, false);
        if (f != null && f.soulStage() > 0) {
            Component st = switch (f.soulStage()) {
                case 1 -> Component.translatable("bloodmoon.map.soul.site");
                case 2 -> Component.translatable("bloodmoon.map.soul.feeding", f.soulPct());
                case 3 -> Component.translatable("bloodmoon.map.soul.awake");
                default -> Component.translatable("bloodmoon.map.soul.slain");
            };
            Component soul = Component.translatable("bloodmoon.map.soul", st);
            g.drawString(font, soul, mapL + mapW - font.width(soul) - 30, 17, f.soulStage() == 3 ? 0xFFFF5070 : 0xFFE080FF, false);
        }
        g.drawString(font, Component.translatable("bloodmoon.map.hint"), mapL, height - 11, 0xFF6A6070, false);
        Component legend = Component.translatable("bloodmoon.map.legend");
        g.drawString(font, legend, mapL + mapW - font.width(legend), height - 11, 0xFF6A6070, false);

        // botón del ojo
        boolean hot = mouseX >= eyeX() && mouseX < eyeX() + 20 && mouseY >= eyeY() && mouseY < eyeY() + 18;
        DominionMapButton.frame(g, eyeX(), eyeY(), 20, 18, hot || showHierarchy);
        g.blit(EYE, eyeX() + 2, eyeY() + 1, 0, 0, 16, 16, 16, 16);
        if (hot) g.renderTooltip(font, Component.translatable("bloodmoon.map.hierarchy"), mouseX, mouseY);
        if (showHierarchy) drawHierarchy(g, f);
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int w, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x0 : x0 + (x1 - x0) * i / n, y = n == 0 ? y0 : y0 + (y1 - y0) * i / n;
            g.fill(x, y, x + w, y + w, color);
        }
    }

    private void drawIcons(GuiGraphics g) {
        int bpp = bpp();
        for (DominionMapPayload.FactionView f : ClientDominion.factions) {
            // caminos y estructuras
            int sideR = f.half() * 2 + 1;
            int ccx0 = f.centerX() >> 4, ccz0 = f.centerZ() >> 4;
            int lw = bpp <= 2 ? 2 : 1;
            for (int gz = 0; gz < sideR; gz++) for (int gx = 0; gx < sideR; gx++) {
                int mask = f.roads()[gz * sideR + gx] & 0xFF;
                if (mask == 0) continue;
                int bx = (ccx0 + gx - f.half()) * 16 + 8, bz = (ccz0 + gz - f.half()) * 16 + 8;
                int x0 = sx(bx), y0 = sz(bz);
                for (int i = 0; i < 8; i++) {
                    if ((mask & 1 << i) == 0) continue;
                    int[] d = com.agustin.bloodmoon.invasion.DominionStructures.DIRS[i];
                    line(g, x0, y0, sx(bx + d[0] * 8), sz(bz + d[1] * 8), lw, 0xFF7A5A90);
                }
            }
            for (int gz = 0; gz < sideR; gz++) for (int gx = 0; gx < sideR; gx++) {
                int st = f.structs()[gz * sideR + gx];
                if (st == 0) continue;
                int x = sx((ccx0 + gx - f.half()) * 16 + 8), y = sz((ccz0 + gz - f.half()) * 16 + 8);
                switch (st) {
                    case com.agustin.bloodmoon.invasion.DominionStructures.NEST -> {
                        g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
                        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFFA02020);
                    }
                    case com.agustin.bloodmoon.invasion.DominionStructures.TOWER -> {
                        g.fill(x - 2, y - 5, x + 2, y + 3, 0xFF000000);
                        g.fill(x - 1, y - 4, x + 1, y + 2, 0xFFD8D0E0);
                    }
                    case com.agustin.bloodmoon.invasion.DominionStructures.SPIRE -> {   // aguja: torre alta con punta violeta
                        g.fill(x - 3, y - 9, x + 3, y + 5, 0xFF000000);
                        g.fill(x - 2, y - 8, x + 2, y + 4, 0xFF8A7A9A);
                        g.fill(x - 1, y - 12, x + 1, y - 8, 0xFF000000);
                        g.fill(x - 1, y - 11, x + 1, y - 8, 0xFFD070FF);
                    }
                    case com.agustin.bloodmoon.invasion.DominionStructures.SOUL -> {   // santuario: anillo y cristal que late
                        int rr = Math.max(6, (int) (37.0 / bpp)) ;
                        int pulse = (int) ((minecraft.level != null ? minecraft.level.getGameTime() : 0) % 40);
                        for (int k = 0; k < 64; k++) {
                            double a = k * Math.PI * 2 / 64;
                            int px = x + (int) Math.round(Math.cos(a) * rr), py = y + (int) Math.round(Math.sin(a) * rr);
                            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFF000000);
                            g.fill(px, py, px + 1, py + 1, 0xFFB050FF);
                        }
                        diamond(g, x, y, 6, 0xFF000000);
                        diamond(g, x, y, 5, pulse < 20 ? 0xFFE080FF : 0xFFA040E0);
                        diamond(g, x, y, 2, 0xFFFFFFFF);
                    }
                    default -> {
                        g.fill(x - 5, y - 5, x + 5, y + 5, 0xFF000000);
                        g.fill(x - 4, y - 4, x + 4, y + 4, 0xFF5A4A6A);
                        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF2A1A36);
                    }
                }
            }
            // obeliscos
            int side = f.half() * 2 + 1;
            int ccx = f.centerX() >> 4, ccz = f.centerZ() >> 4;
            int s = bpp <= 4 ? 3 : 2;
            for (int gz = 0; gz < side; gz++) for (int gx = 0; gx < side; gx++) {
                if ((f.grid()[gz * side + gx] & 0xFF) != 101) continue;
                int x = sx((ccx + gx - f.half()) * 16 + 8), y = sz((ccz + gz - f.half()) * 16 + 8);
                g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, 0xFF000000);
                g.fill(x - s, y - s, x + s, y + s, 0xFFB04CFF);
            }
            // rangos con puesto: Capitanes (triángulo dorado) y Generales (rombo rojo)
            for (DominionMapPayload.RankView r : f.ranks()) {
                if (!r.alive() || r.seatX() == Integer.MIN_VALUE) continue;
                int rx = sx(r.seatX()), ry = sz(r.seatZ());
                if (r.rank() == InvasionRank.GENERAL.ordinal()) {
                    diamond(g, rx, ry, 5, 0xFF000000);
                    diamond(g, rx, ry, 4, 0xFFE03030);
                } else if (r.rank() == InvasionRank.CAPTAIN.ordinal()) {
                    for (int k = 0; k <= 4; k++) g.fill(rx - k, ry - 4 + k, rx + k + 1, ry - 3 + k, 0xFF000000);
                    for (int k = 0; k <= 3; k++) g.fill(rx - k, ry - 3 + k, rx + k + 1, ry - 2 + k, 0xFFF0C040);
                }
            }
            // eje: el coliseo
            int x = sx(f.centerX()), y = sz(f.centerZ());
            diamond(g, x, y, 6, 0xFF000000);
            diamond(g, x, y, 5, f.active() ? 0xFFD070FF : 0xFF70D090);
            diamond(g, x, y, 2, 0xFF1A0C22);
            if (bpp <= 8) g.drawCenteredString(font, f.name(), x, y - 16, f.active() ? 0xFFE0B0FF : 0xFFA0E0B0);
        }
        // Puertas de Guerra abiertas (asaltos en curso)
        boolean blink = (minecraft.level != null ? minecraft.level.getGameTime() : 0) / 10 % 2 == 0;
        for (long gp : ClientDominion.gates) {
            net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(gp);
            int x = sx(p.getX()), y = sz(p.getZ());
            int c = blink ? 0xFFFF3030 : 0xFFA01010;
            for (int k = -4; k <= 4; k++) {
                g.fill(x + k, y + k, x + k + 2, y + k + 2, c);
                g.fill(x + k, y - k, x + k + 2, y - k + 2, c);
            }
        }
        // jugador: flecha grande con el rumbo, anillo que late y nombre; si está fuera de la vista, flecha en el borde
        if (minecraft.player != null) drawPlayer(g);
    }

    private void drawPlayer(GuiGraphics g) {
        double px = sx(minecraft.player.getX()), py = sz(minecraft.player.getZ());
        boolean inside = px >= mapL + 6 && px < mapL + mapW - 6 && py >= mapT + 6 && py < mapT + mapH - 6;
        float t = (minecraft.level != null ? minecraft.level.getGameTime() : 0) + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        if (!inside) {
            double cx = mapL + mapW / 2.0, cy = mapT + mapH / 2.0, dx = px - cx, dy = py - cy;
            double k = Math.min((mapW / 2.0 - 10) / Math.max(1e-3, Math.abs(dx)), (mapH / 2.0 - 10) / Math.max(1e-3, Math.abs(dy)));
            px = cx + dx * k;
            py = cy + dy * k;
            arrow(g, px, py, (float) Math.atan2(dx, -dy), 7, 0xFF000000, 0xFFFFD040);
            return;
        }
        int r = 7 + (int) (2 * Mth.sin(t * 0.2F));
        ring(g, (int) px, (int) py, r + 2, 0x60FFD040);
        ring(g, (int) px, (int) py, r, 0xC0FFD040);
        // rumbo: en el mapa, -Z es arriba; el yaw de Minecraft mira a +Z en 0
        float heading = (minecraft.player.getYRot() + 180F) * Mth.DEG_TO_RAD;
        arrow(g, px, py, heading, 8, 0xFF000000, 0xFFFFE070);
        String name = minecraft.player.getGameProfile().getName();
        g.drawCenteredString(font, name, (int) px, (int) py + 11, 0xFFFFE070);
    }

    /** Flecha rellena apuntando a {@code ang} (0 = arriba, en sentido horario). */
    private static void arrow(GuiGraphics g, double cx, double cy, float ang, int size, int outline, int fill) {
        tri(g, cx, cy, ang, size + 1.6, outline);
        tri(g, cx, cy, ang, size, fill);
    }

    private static void tri(GuiGraphics g, double cx, double cy, float ang, double s, int color) {
        double sn = Math.sin(ang), cs = Math.cos(ang);
        double[][] p = {{0, -s}, {-s * 0.7, s * 0.75}, {0, s * 0.35}, {s * 0.7, s * 0.75}};
        double[][] q = new double[4][2];
        for (int i = 0; i < 4; i++) {
            q[i][0] = cx + p[i][0] * cs - p[i][1] * sn;
            q[i][1] = cy + p[i][0] * sn + p[i][1] * cs;
        }
        // dos triángulos: punta-izquierda-muesca y punta-muesca-derecha
        fillTri(g, q[0], q[1], q[2], color);
        fillTri(g, q[0], q[2], q[3], color);
    }

    private static void fillTri(GuiGraphics g, double[] a, double[] b, double[] c, int color) {
        int y0 = (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))), y1 = (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1])));
        for (int y = y0; y <= y1; y++) {
            double yy = y + 0.5, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            double[][][] edges = {{a, b}, {b, c}, {c, a}};
            for (double[][] e : edges) {
                double ya = e[0][1], yb = e[1][1];
                if ((yy < Math.min(ya, yb)) || (yy > Math.max(ya, yb)) || ya == yb) continue;
                double x = e[0][0] + (yy - ya) / (yb - ya) * (e[1][0] - e[0][0]);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (lo <= hi) g.fill((int) Math.round(lo), y, (int) Math.round(hi) + 1, y + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int k = 0; k < 48; k++) {
            double a = k * Math.PI * 2 / 48;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void diamond(GuiGraphics g, int x, int y, int r, int color) {
        for (int k = -r; k <= r; k++) {
            int w = r - Math.abs(k);
            g.fill(x - w, y + k, x + w + 1, y + k + 1, color);
        }
    }

    private void drawHierarchy(GuiGraphics g, DominionMapPayload.FactionView f) {
        int w = 190, x = eyeX() + 20 - w, y = eyeY() + 22;
        if (f == null) {
            g.fill(x, y, x + w, y + 20, 0xE0100A16);
            g.drawString(font, Component.translatable("bloodmoon.map.none"), x + 6, y + 6, 0xFF8A8090, false);
            return;
        }
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("bloodmoon.map.hierarchy.title", f.name()).withStyle(ChatFormatting.BOLD, ChatFormatting.LIGHT_PURPLE));
        for (InvasionRank rank : new InvasionRank[]{InvasionRank.KING, InvasionRank.GENERAL, InvasionRank.CAPTAIN}) {
            long alive = f.ranks().stream().filter(r -> r.rank() == rank.ordinal() && r.alive()).count();
            lines.add(Component.translatable("bloodmoon.map.hierarchy.count", rank.pluralName(), alive, rank.max).withStyle(ChatFormatting.WHITE));
        }
        lines.add(Component.translatable("bloodmoon.map.hierarchy.plain", InvasionRank.FORGER.pluralName(), f.forgers()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("bloodmoon.map.hierarchy.plain", InvasionRank.TROOPS.pluralName(), "~" + f.troops()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        for (InvasionRank rank : new InvasionRank[]{InvasionRank.KING, InvasionRank.GENERAL, InvasionRank.CAPTAIN}) {
            for (DominionMapPayload.RankView r : f.ranks()) {
                if (r.rank() != rank.ordinal()) continue;
                Component line = Component.translatable("bloodmoon.map.hierarchy.named", rank.displayName(), r.name())
                        .withStyle(r.alive() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY);
                if (!r.alive()) line = line.copy().append(Component.translatable("bloodmoon.map.hierarchy.fallen").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(line);
            }
        }
        int h = lines.size() * 11 + 10;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF6A2A9E);
        g.fill(x, y, x + w, y + h, 0xF0100A16);
        int ly = y + 6;
        for (Component c : lines) {
            g.drawString(font, c, x + 6, ly, 0xFFFFFFFF, false);
            ly += 11;
        }
    }

    // ------------------------------------------------------------------ controles

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= eyeX() && mouseX < eyeX() + 20 && mouseY >= eyeY() && mouseY < eyeY() + 18) {
            showHierarchy = !showHierarchy;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MapKey.OPEN_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && minecraft.player != null) {   // centrar en el jugador
            viewX = minecraft.player.getX();
            viewZ = minecraft.player.getZ();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        viewX -= dragX * bpp();
        viewZ -= dragY * bpp();
        dirty = true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int old = bpp();
        int z = Mth.clamp(zoom - (int) Math.signum(scrollY), 0, ZOOMS.length - 1);
        if (z == zoom) return true;
        double cx = mapL + mapW / 2.0, cy = mapT + mapH / 2.0;
        double wx = viewX + (mouseX - cx) * old, wz = viewZ + (mouseY - cy) * old;
        zoom = z;
        viewX = wx - (mouseX - cx) * bpp();
        viewZ = wz - (mouseY - cy) * bpp();
        dirty = true;
        return true;
    }
}

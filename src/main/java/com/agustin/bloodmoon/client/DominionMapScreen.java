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

    private void drawIcons(GuiGraphics g) {
        int bpp = bpp();
        for (DominionMapPayload.FactionView f : ClientDominion.factions) {
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
        // jugador
        if (minecraft.player != null) {
            int x = sx(minecraft.player.getX()), y = sz(minecraft.player.getZ());
            g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
            g.fill(x - 2, y - 2, x + 2, y + 2, 0xFFFFFFFF);
            float yaw = minecraft.player.getYRot() * Mth.DEG_TO_RAD;
            for (int k = 3; k <= 6; k++) {
                int px = x + Math.round(-Mth.sin(yaw) * k), py = y + Math.round(Mth.cos(yaw) * k);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
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

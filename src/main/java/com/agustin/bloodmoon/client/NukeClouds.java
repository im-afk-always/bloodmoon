package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.network.NukePayload;
import com.agustin.bloodmoon.registry.ModParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Hongos de fuego púrpura de las cargas del dragón. Cada hongo es una coreografía de "bocanadas"
 * (partículas grandes y autoiluminadas): fogonazo, bola de fuego, tallo, sombrero toroidal que rueda,
 * cúpula y onda de choque en la base. El tamaño escala con la potencia (x3 / x5 / x10).
 * Además: destello púrpura en pantalla y en el cielo, temblor de cámara y estruendo con retardo por distancia.
 */
public final class NukeClouds {
    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static SpriteSet sprites;

    private NukeClouds() {}

    static final int CAP = 0, DOME = 1, STEM = 2, SURGE = 3, FLASH = 4, FIREBALL = 5;

    /** Parámetros compartidos de un hongo. Unidades en bloques y ticks. */
    static final class Cloud {
        final double x, y, z;
        final int mult;
        final float height, radius, life, rise;
        float age;
        boolean boomPlayed;

        Cloud(double x, double y, double z, int mult) {
            this.x = x; this.y = y; this.z = z; this.mult = mult;
            this.height = 8F * mult;
            this.radius = 3F * mult;
            this.life = 140 + 14 * mult;
            this.rise = 30 + 5 * mult;
        }

        float riseK(float a) {
            float t = Mth.clamp(a / rise, 0F, 1F);
            return 1F - (1F - t) * (1F - t);
        }

        float capY(float a) {
            return height * riseK(a);
        }

        float capR(float a) {
            return radius * (0.3F + 0.7F * riseK(a)) * (1F + 0.25F * a / life);
        }

        float heat(float a) {
            return (float) Math.exp(-a / (0.35F * life));
        }

        float fade(float a) {
            float in = Mth.clamp(a / 4F, 0F, 1F);
            float out = 1F - smooth((a - 0.55F * life) / (0.45F * life));
            return in * out;
        }
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    // ------------------------------------------------------------------ registro

    public static void init() {
        NukePayload.handler = p -> Minecraft.getInstance().execute(() -> spawn(p.x(), p.y(), p.z(), p.mult()));
    }

    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.NUKE_PUFF.get(), set -> {
            sprites = set;
            return (type, level, x, y, z, dx, dy, dz) -> null;
        });
    }

    public static void reset() {
        CLOUDS.clear();
    }

    // ------------------------------------------------------------------ creación

    public static void spawn(double x, double y, double z, int mult) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || sprites == null) return;
        Cloud c = new Cloud(x, y, z, mult);
        CLOUDS.add(c);
        RandomSource r = level.random;

        add(level, c, FLASH, 0, 0, 0, 1F);
        add(level, c, FLASH, 0, 0, 0.5F, 0.7F);
        for (int i = 0; i < 3; i++) add(level, c, FIREBALL, r.nextFloat() * 6.28F, r.nextFloat(), r.nextFloat(), 0.9F + r.nextFloat() * 0.3F);
        int cap = 28 + 6 * mult;
        for (int i = 0; i < cap; i++) {
            add(level, c, CAP, i * 6.2832F / cap + r.nextFloat() * 0.2F, r.nextFloat() * 6.2832F, r.nextFloat(), 0.8F + r.nextFloat() * 0.45F);
        }
        for (int i = 0; i < 8 + 2 * mult; i++) {
            add(level, c, DOME, r.nextFloat() * 6.2832F, (float) Math.sqrt(r.nextFloat()), r.nextFloat(), 0.8F + r.nextFloat() * 0.4F);
        }
        int stem = 12 + 3 * mult;
        for (int i = 0; i < stem; i++) {
            add(level, c, STEM, (i + r.nextFloat()) / stem, r.nextFloat() * 6.2832F, r.nextFloat(), 0.8F + r.nextFloat() * 0.4F);
        }
        int surge = 18 + 3 * mult;
        for (int i = 0; i < surge; i++) {
            add(level, c, SURGE, i * 6.2832F / surge + r.nextFloat() * 0.3F, r.nextFloat(), r.nextFloat(), 0.8F + r.nextFloat() * 0.5F);
        }
    }

    private static void add(ClientLevel level, Cloud c, int role, float a0, float a1, float a2, float size) {
        Minecraft.getInstance().particleEngine.add(new Puff(level, c, role, a0, a1, a2, size, sprites));
    }

    // ------------------------------------------------------------------ tick / luz / temblor / sonido

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        CLOUDS.removeIf(c -> c.age > c.life);
        for (Cloud c : CLOUDS) {
            c.age++;
            if (!c.boomPlayed && player != null) {
                double dist = Math.sqrt(player.distanceToSqr(c.x, c.y, c.z));
                if (c.age >= dist / 34.0) {   // el sonido llega con retardo (sonido "de película", más rápido que el real)
                    c.boomPlayed = true;
                    float vol = (float) Mth.clamp(c.mult * 40.0 / (dist + 20.0), 0.15, 3.0);
                    mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                            SoundSource.HOSTILE, vol, 0.35F, false);
                    mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                            SoundSource.WEATHER, vol * 0.8F, 0.45F, false);
                }
            }
        }
    }

    private static float proximity(Cloud c, Vec3 eye) {
        double d = Math.sqrt(eye.distanceToSqr(c.x, c.y + c.radius, c.z));
        return (float) Mth.clamp(c.radius * 10.0 / (d + 1.0), 0.0, 1.0);
    }

    /** Destello inicial (pantalla). */
    private static float flash(float pt) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vec3 look = new Vec3(cam.getLookVector());
        float total = 0F;
        for (Cloud c : CLOUDS) {
            float a = c.age + pt;
            float k = (float) Math.exp(-a / 7.0) * proximity(c, eye);
            Vec3 to = new Vec3(c.x - eye.x, c.y + c.radius - eye.y, c.z - eye.z).normalize();
            k *= 0.45F + 0.55F * (float) Math.max(0, look.dot(to));
            total += k;
        }
        return Math.min(0.9F, total);
    }

    /** Resplandor púrpura duradero (cielo / niebla). */
    public static float glow(float pt) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float total = 0F;
        for (Cloud c : CLOUDS) {
            float a = c.age + pt;
            total += c.heat(a) * c.fade(a) * proximity(c, eye) * 0.7F;
        }
        return Math.min(0.75F, total);
    }

    public static void renderFlash(GuiGraphics g, DeltaTracker delta) {
        if (CLOUDS.isEmpty()) return;
        float k = flash(delta.getGameTimeDeltaPartialTick(false));
        if (k < 0.01F) return;
        RenderSystem.enableBlend();
        int a = (int) (k * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xE6B4FF);
        RenderSystem.disableBlend();
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (CLOUDS.isEmpty()) return;
        float k = glow((float) event.getPartialTick());
        if (k <= 0F) return;
        event.setRed(Mth.lerp(k, event.getRed(), 0.62F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.22F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.95F));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (CLOUDS.isEmpty()) return;
        Vec3 eye = event.getCamera().getPosition();
        float pt = (float) event.getPartialTick();
        float shake = 0F;
        float time = 0F;
        for (Cloud c : CLOUDS) {
            double d = Math.sqrt(eye.distanceToSqr(c.x, c.y, c.z));
            float arrival = (float) (d / 34.0);
            float a = c.age + pt - arrival;
            if (a < 0) continue;
            shake += (float) (c.mult * 0.9 / (1.0 + d / 25.0) * Math.exp(-a / 18.0));
            time = c.age + pt;
        }
        if (shake < 0.02F) return;
        shake = Math.min(shake, 4F);
        event.setPitch(event.getPitch() + Mth.sin(time * 2.1F) * shake);
        event.setYaw(event.getYaw() + Mth.sin(time * 1.7F + 1F) * shake * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(time * 2.9F + 2F) * shake * 0.5F);
    }

    // ------------------------------------------------------------------ bocanada

    static final class Puff extends TextureSheetParticle {
        private final Cloud c;
        private final int role;
        private final float a0, a1, a2, sizeK, flick;

        Puff(ClientLevel level, Cloud c, int role, float a0, float a1, float a2, float sizeK, SpriteSet sprites) {
            super(level, c.x, c.y, c.z);
            this.c = c;
            this.role = role;
            this.a0 = a0; this.a1 = a1; this.a2 = a2;
            this.sizeK = sizeK;
            this.flick = random.nextFloat() * 10F;
            this.lifetime = role == FLASH ? 40 : (int) c.life;
            this.hasPhysics = false;
            this.gravity = 0F;
            this.friction = 1F;
            this.roll = this.oRoll = random.nextFloat() * 6.2832F;
            this.pickSprite(sprites);
            update(0F);
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            update(this.age);
        }

        private void update(float a) {
            float capY = c.capY(a), rc = c.capR(a), heat = c.heat(a);
            double px = c.x, py = c.y, pz = c.z;
            float size, local, alpha = c.fade(a);
            float spin = 0.004F;
            switch (role) {
                case FLASH -> {
                    size = c.radius * (1.2F + 2.6F * (1F - (float) Math.exp(-a / 3F))) * sizeK;
                    py += c.radius * 0.4F + a2 * c.radius;
                    local = 1.6F;
                    alpha = (float) Math.exp(-a / 6F);
                }
                case FIREBALL -> {
                    py += Math.max(c.radius * 0.5F, capY);
                    px += Mth.cos(a0) * rc * 0.15F * a1;
                    pz += Mth.sin(a0) * rc * 0.15F * a1;
                    size = rc * 0.75F * sizeK;
                    local = heat * 1.4F;
                    alpha *= Mth.clamp(heat * 2F, 0.2F, 1F);
                }
                case CAP -> {
                    float major = 0.75F * rc, minor = 0.45F * rc;
                    float phi = a1 + a * 0.045F;         // el sombrero "rueda" hacia afuera
                    float rr = major + minor * Mth.cos(phi);
                    px += Mth.cos(a0) * rr;
                    pz += Mth.sin(a0) * rr;
                    py += capY + minor * 0.75F * Mth.sin(phi);
                    size = 0.6F * rc * sizeK;
                    local = heat * (0.65F - 0.45F * Mth.sin(phi));
                    spin = 0.01F;
                }
                case DOME -> {
                    float rr = a1 * 0.6F * rc;
                    px += Mth.cos(a0) * rr;
                    pz += Mth.sin(a0) * rr;
                    py += capY + 0.2F * rc + 0.5F * rc * (float) Math.sqrt(Math.max(0, 1 - a1 * a1));
                    size = 0.55F * rc * sizeK;
                    local = heat * 0.4F;
                }
                case STEM -> {
                    float top = Math.max(0F, capY - 0.3F * rc);
                    float h = ((a0 + a * 0.004F) % 1F) * top;
                    float hk = c.height > 0 ? h / c.height : 0F;
                    float rr = 0.22F * c.radius * (1F - 0.35F * hk) * (0.4F + 0.6F * a2);
                    px += Mth.cos(a1) * rr;
                    pz += Mth.sin(a1) * rr;
                    py += h;
                    size = 0.38F * c.radius * sizeK * (0.75F + 0.45F * (1F - hk));
                    local = heat * (1.1F - 0.6F * hk);
                }
                default -> { // SURGE
                    float rr = (float) (1.5F * c.radius * (1 - Math.exp(-a / 12F))) * (0.8F + 0.3F * a2);
                    px += Mth.cos(a0) * rr;
                    pz += Mth.sin(a0) * rr;
                    py += 0.6F + 0.15F * c.radius * a1;
                    size = 0.45F * c.radius * sizeK * (0.7F + 0.6F * Mth.clamp(a / 20F, 0F, 1F));
                    local = heat * 0.5F;
                    alpha *= 1F - smooth((a - 0.35F * c.life) / (0.3F * c.life));
                }
            }
            local *= 0.85F + 0.15F * Mth.sin(a * 0.7F + flick);
            // humo púrpura oscuro -> fuego púrpura -> blanco violáceo
            float fire = Mth.clamp(local * 1.5F, 0F, 1F);
            float hot = Mth.clamp((local - 0.6F) * 2.5F, 0F, 1F);
            float r = Mth.lerp(fire, 0.24F, 0.78F), g = Mth.lerp(fire, 0.13F, 0.26F), b = Mth.lerp(fire, 0.32F, 1.0F);
            r = Mth.lerp(hot, r, 1.0F); g = Mth.lerp(hot, g, 0.86F); b = Mth.lerp(hot, b, 1.0F);
            this.setColor(r, g, b);
            this.setAlpha(Mth.clamp(alpha * (role == FLASH ? 1F : 0.92F), 0F, 1F));
            this.quadSize = Math.max(0.1F, size);
            this.setSize(this.quadSize * 2F, this.quadSize * 2F);
            this.setPos(px, py, pz);
            this.roll += spin * (a2 > 0.5F ? 1 : -1);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880; // la nube se ilumina desde adentro
        }
    }
}

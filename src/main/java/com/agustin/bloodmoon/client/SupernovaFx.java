package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.network.SupernovaPayload;
import com.agustin.bloodmoon.registry.ModParticles;
import com.agustin.bloodmoon.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Supernova del Dragón de la Primera Alma (cliente):
 *  0-100   el dragón asciende y una luz violeta nace de su alma y crece
 *  60-104  la luz se vuelve blanca y se expande; el cielo se aclara
 *  104-125 colapsa en un punto, todo se oscurece y la luz es absorbida (sonido de carga)
 *  125     ESTALLIDO: destello blanco enceguecedor, esfera de luz gigante, anillo de choque,
 *          hongo de fuego, temblor y estruendo (con retardo por distancia)
 */
public final class SupernovaFx {
    private static final List<Nova> NOVAS = new ArrayList<>();
    private static SpriteSet sprites;

    private static final int R = SupernovaPayload.RISE_END, C = SupernovaPayload.COLLAPSE_START, D = SupernovaPayload.DETONATE;
    private static final int CORE = 0, SHELL = 1, RING = 2;

    private SupernovaFx() {}

    static final class Nova {
        final double x, y, z, groundY;
        final float scale, k;
        float age;
        boolean chargePlayed, blastPlayed, detonated;

        Nova(SupernovaPayload p) {
            this.x = p.x(); this.y = p.y(); this.z = p.z(); this.groundY = p.groundY();
            this.scale = p.scale();
            this.k = p.scale() / 5F;
        }

        Vec3 core(float a) {
            return new Vec3(x, y + Math.min(a, R) * SupernovaPayload.RISE_SPEED, z);
        }

        /** Radio de la luz del alma antes del estallido. */
        float coreSize(float a) {
            float s;
            if (a < 60) s = Mth.lerp(a / 60F, 1.5F, 6F);
            else if (a < C) s = Mth.lerp(smooth((a - 60) / (C - 60F)), 6F, 16F);
            else {
                float t = 1F - smooth((a - C) / (float) (D - C));
                s = 16F * t * t + 0.3F;
            }
            return s * k;
        }
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    public static void init() {
        SupernovaPayload.handler = p -> Minecraft.getInstance().execute(() -> start(p));
    }

    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SUPERNOVA_GLOW.get(), set -> {
            sprites = set;
            return (type, level, x, y, z, dx, dy, dz) -> null;
        });
    }

    public static void reset() {
        NOVAS.clear();
    }

    private static void start(SupernovaPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sprites == null) return;
        Nova n = new Nova(p);
        NOVAS.add(n);
        // tres capas de luz alrededor del alma
        add(level, n, CORE, 1F, 1F, 0);
        add(level, n, CORE, 1.9F, 0.55F, 0);
        add(level, n, CORE, 3.4F, 0.28F, 0);
    }

    private static void add(ClientLevel level, Nova n, int role, float sizeK, float alphaK, float angle) {
        Minecraft.getInstance().particleEngine.add(new Glow(level, n, role, sizeK, alphaK, angle, sprites));
    }

    // ------------------------------------------------------------------ tick

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null) return;
        NOVAS.removeIf(n -> n.age > D + 260);
        for (Nova n : NOVAS) {
            n.age++;
            float a = n.age;
            Vec3 core = n.core(a);
            double dist = player != null ? Math.sqrt(player.distanceToSqr(core)) : 0;

            // chispas que nacen del alma / luz absorbida durante el colapso
            if (a < C) {
                for (int i = 0; i < 4; i++) {
                    Vec3 dir = randomDir(level);
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, core.x, core.y, core.z,
                            dir.x * 0.6 * n.k, dir.y * 0.6 * n.k, dir.z * 0.6 * n.k);
                }
            } else if (a < D) {
                for (int i = 0; i < 14; i++) {
                    Vec3 dir = randomDir(level);
                    double r = 30 * n.k;
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, core.x + dir.x * r, core.y + dir.y * r, core.z + dir.z * r,
                            -dir.x * r / 12, -dir.y * r / 12, -dir.z * r / 12);
                }
            }
            if (!n.chargePlayed && a >= C - 4 && player != null) {
                n.chargePlayed = true;
                playAtPlayer(ModSounds.SUPERNOVA_CHARGE.get(), volume(dist, 1.5F), 1F);
            }
            if (!n.detonated && a >= D) {
                n.detonated = true;
                add(level, n, SHELL, 1F, 1F, 0);
                add(level, n, SHELL, 0.55F, 1F, 0);
                for (int i = 0; i < 28; i++) add(level, n, RING, 1F, 0.8F, i * Mth.TWO_PI / 28);
                NukeClouds.spawn(n.x, n.groundY, n.z, 10);   // columna de fuego desde el cráter
                for (int i = 0; i < 200; i++) {
                    Vec3 dir = randomDir(level);
                    double sp = (1.5 + level.random.nextDouble() * 3) * n.k;
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, core.x, core.y, core.z, dir.x * sp, dir.y * sp, dir.z * sp);
                }
            }
            if (!n.blastPlayed && a >= D + dist / 34.0 && player != null) {
                n.blastPlayed = true;
                float v = volume(dist, 4F);
                playAtPlayer(ModSounds.SUPERNOVA_BLAST.get(), Math.max(v, 0.6F), 1F);
                playAtPlayer(SoundEvents.GENERIC_EXPLODE.value(), v, 0.3F);
                playAtPlayer(SoundEvents.WARDEN_SONIC_BOOM, v, 0.5F);
                playAtPlayer(SoundEvents.LIGHTNING_BOLT_THUNDER, v, 0.4F);
            }
        }
    }

    private static Vec3 randomDir(ClientLevel level) {
        return new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).normalize();
    }

    private static float volume(double dist, float near) {
        return (float) Mth.clamp(near * 120.0 / (dist + 60.0), 0.35, near);
    }

    private static void playAtPlayer(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), sound, SoundSource.HOSTILE, volume, pitch, false);
    }

    // ------------------------------------------------------------------ pantalla / cielo / cámara

    private static float proximity(Nova n, Vec3 eye, float a) {
        double d = Math.sqrt(eye.distanceToSqr(n.core(a)));
        return (float) Mth.clamp(700.0 * n.k / (d + 1.0), 0.0, 1.0);
    }

    /** Blanco que cubre la pantalla (0..1) y oscurecimiento durante el colapso. */
    private static float[] screen(float pt) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vec3 look = new Vec3(cam.getLookVector());
        float white = 0F, dark = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            float prox = proximity(n, eye, a);
            Vec3 to = n.core(a).subtract(eye).normalize();
            float facing = 0.55F + 0.45F * (float) Math.max(0, look.dot(to));
            if (a < C) {
                white += 0.22F * smooth((a - 50) / (C - 50F)) * prox * facing;
            } else if (a < D) {
                dark += 0.35F * smooth((a - C) / (float) (D - C)) * prox;
            } else {
                float t = a - D;
                float w = t < 18 ? 1F : (float) Math.exp(-(t - 18) / 35.0);
                white += w * Math.max(prox, 0.6F) * (t < 18 ? 1F : 0.75F + 0.25F * facing);
            }
        }
        return new float[]{Math.min(1F, white), Math.min(0.6F, dark)};
    }

    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (NOVAS.isEmpty()) return;
        float[] s = screen(delta.getGameTimeDeltaPartialTick(false));
        RenderSystem.enableBlend();
        if (s[1] > 0.01F) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (s[1] * 255) << 24) | 0x0A0414);
        if (s[0] > 0.01F) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (s[0] * 255) << 24) | 0xFFFCFF);
        RenderSystem.disableBlend();
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (NOVAS.isEmpty()) return;
        float pt = (float) event.getPartialTick();
        Vec3 eye = event.getCamera().getPosition();
        float violet = 0F, white = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            float prox = proximity(n, eye, a);
            if (a < D) violet += smooth(a / C) * 0.5F * prox * (1F - smooth((a - C) / (float) (D - C)));
            else white += (float) Math.exp(-(a - D) / 60.0) * Math.max(prox, 0.5F);
        }
        violet = Math.min(0.6F, violet);
        white = Math.min(1F, white);
        event.setRed(Mth.lerp(white, Mth.lerp(violet, event.getRed(), 0.7F), 1F));
        event.setGreen(Mth.lerp(white, Mth.lerp(violet, event.getGreen(), 0.45F), 0.97F));
        event.setBlue(Mth.lerp(white, Mth.lerp(violet, event.getBlue(), 1F), 1F));
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (NOVAS.isEmpty()) return;
        Vec3 eye = event.getCamera().getPosition();
        float pt = (float) event.getPartialTick();
        float shake = 0F, time = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            time = a;
            if (a > C && a < D) shake += 0.25F * smooth((a - C) / (float) (D - C)); // vibra mientras colapsa
            double d = Math.sqrt(eye.distanceToSqr(n.x, n.groundY, n.z));
            float hit = a - D - (float) (d / 34.0);
            if (hit >= 0) shake += (float) (6.0 * n.k / (1.0 + d / 120.0) * Math.exp(-hit / 40.0));
        }
        if (shake < 0.02F) return;
        shake = Math.min(shake, 7F);
        event.setPitch(event.getPitch() + Mth.sin(time * 2.3F) * shake);
        event.setYaw(event.getYaw() + Mth.sin(time * 1.9F + 1F) * shake * 0.7F);
        event.setRoll(event.getRoll() + Mth.sin(time * 3.1F + 2F) * shake * 0.6F);
    }

    // ------------------------------------------------------------------ partícula de luz

    static final class Glow extends TextureSheetParticle {
        private final Nova n;
        private final int role;
        private final float sizeK, alphaK, angle;

        Glow(ClientLevel level, Nova n, int role, float sizeK, float alphaK, float angle, SpriteSet sprites) {
            super(level, n.x, n.y, n.z);
            this.n = n;
            this.role = role;
            this.sizeK = sizeK;
            this.alphaK = alphaK;
            this.angle = angle;
            this.hasPhysics = false;
            this.gravity = 0F;
            this.friction = 1F;
            this.lifetime = role == CORE ? D + 2 - (int) n.age : role == SHELL ? 120 : 160;
            this.pickSprite(sprites);
            update(n.age);
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            update(n.age);
        }

        private void update(float a) {
            Vec3 core = n.core(a);
            float size, alpha, r = 1F, g = 1F, b = 1F;
            double px = core.x, py = core.y, pz = core.z;
            switch (role) {
                case CORE -> {
                    size = n.coreSize(a) * sizeK;
                    float w = smooth((a - 30) / 70F);         // violeta -> blanco
                    r = Mth.lerp(w, 0.8F, 1F); g = Mth.lerp(w, 0.5F, 1F);
                    alpha = alphaK * (0.85F + 0.15F * Mth.sin(a * 0.9F)) * smooth(a / 10F);
                }
                case SHELL -> {
                    float t = a - D;
                    size = (10F + 260F * (1F - (float) Math.exp(-t / 6F))) * n.k * sizeK;
                    alpha = (float) Math.exp(-t / (sizeK < 1F ? 12F : 28F));
                    g = Mth.lerp(Mth.clamp(t / 60F, 0F, 1F), 1F, 0.75F);
                }
                default -> { // RING: onda de choque horizontal
                    float t = a - D;
                    float rr = (float) (420.0 * n.k * (1 - Math.exp(-t / 30.0)));
                    px += Mth.cos(angle) * rr;
                    pz += Mth.sin(angle) * rr;
                    py = Mth.lerp(Mth.clamp(t / 40F, 0F, 1F), core.y, n.groundY + 10);
                    size = (18F + rr * 0.12F) * n.k;
                    alpha = alphaK * (float) Math.exp(-t / 55.0);
                    r = 0.95F; g = 0.85F;
                }
            }
            this.setColor(r, g, b);
            this.setAlpha(Mth.clamp(alpha, 0F, 1F));
            this.quadSize = Math.max(0.1F, size);
            this.setSize(Math.min(this.quadSize * 2F, 512F), Math.min(this.quadSize * 2F, 512F));
            this.setPos(px, py, pz);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880;
        }
    }
}

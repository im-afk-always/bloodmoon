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
 *  0-80    el dragón asciende y una luz violeta nace de su alma
 *  80-240  la luz se vuelve blanca, enceguecedora, y se expande muchísimo (8 s)
 *  240-320 se contrae hasta un punto; todo se oscurece salvo esa luz; latidos y la luz absorbida (4 s)
 *  320     ESTALLIDO: destello blanco total, esfera de luz gigante, anillo de choque,
 *          hongo de fuego, sacudida fuerte y estruendo (con retardo por distancia)
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
        boolean chargePlayed, blastPlayed, detonated, humPlayed;
        int nextBeat = C;

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
            if (a < R) s = Mth.lerp(a / R, 1.5F, 8F);
            else if (a < C) s = Mth.lerp(smooth((a - R) / (float) (C - R)), 8F, 60F);
            else {
                float t = (a - C) / (float) (D - C);
                float shrink = 1F - t * t * (3F - 2F * t);
                s = 60F * shrink * shrink + 0.35F;
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
        NOVAS.removeIf(n -> n.age > D + 300);
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
                for (int i = 0; i < 18; i++) {
                    Vec3 dir = randomDir(level);
                    double r = 70 * n.k;
                    level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, core.x + dir.x * r, core.y + dir.y * r, core.z + dir.z * r,
                            -dir.x * r / 18, -dir.y * r / 18, -dir.z * r / 18);
                }
            }
            if (!n.humPlayed && a >= R && player != null) {
                n.humPlayed = true;
                playAtPlayer(SoundEvents.BEACON_ACTIVATE, volume(dist, 1.5F), 0.5F);
                playAtPlayer(SoundEvents.ENDER_DRAGON_GROWL, volume(dist, 1.2F), 0.35F);
            }
            // latidos cada vez más rápidos mientras la luz se contrae
            if (a >= n.nextBeat && a < D - 10 && player != null) {
                float p = (a - C) / (float) (D - C);
                playAtPlayer(SoundEvents.WARDEN_HEARTBEAT, volume(dist, 1.6F), 0.55F + 0.3F * p);
                n.nextBeat = (int) a + Math.max(5, (int) (22 - 17 * p));
            }
            if (!n.chargePlayed && a >= D - 28 && player != null) {
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
            if (!n.blastPlayed && a >= D + dist / SupernovaPayload.SHOCK_SPEED && player != null) {
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

    /** Blanco que cubre la pantalla, oscuridad del colapso (0..1) y la nova que manda la oscuridad. */
    private static float[] screen(float pt) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vec3 look = new Vec3(cam.getLookVector());
        float white = 0F, dark = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            float prox = proximity(n, eye, a);
            Vec3 to = n.core(a).subtract(eye).normalize();
            float facing = 0.5F + 0.5F * (float) Math.max(0, look.dot(to));
            float reach = Math.max(prox, 0.65F);
            if (a < C) {
                // la luz crece hasta dejar la pantalla completamente blanca
                float glare = smooth((a - 40) / (C - 60F));
                float view = facing + (1F - facing) * glare;      // al final encandila aunque no la mires
                white += glare * view * reach * (0.96F + 0.04F * Mth.sin(a * 1.3F));
            } else if (a < D) {
                float t = a - C;
                white += (1F - smooth(t / 25F)) * reach;
                dark += smooth(t / 30F) * reach;
            } else {
                float t = a - D;
                float w = t < 25 ? 1F : (float) Math.exp(-(t - 25) / 40.0);
                white += w * Math.max(prox, 0.6F) * (t < 25 ? 1F : 0.75F + 0.25F * facing);
                dark += 1F - smooth(t / 3F);
            }
        }
        return new float[]{Math.min(1F, white), Math.min(0.97F, dark)};
    }

    private static final net.minecraft.resources.ResourceLocation LIGHT_TEX = net.minecraft.resources.ResourceLocation
            .fromNamespaceAndPath(com.agustin.bloodmoon.BloodMoonMod.MODID, "textures/misc/nova_light.png");

    /** Posición en pantalla (px) y px por bloque a esa distancia; null si está detrás de la cámara. */
    static float[] project(Vec3 p, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = p.subtract(cam.getPosition());
        org.joml.Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = v.x * look.x() + v.y * look.y() + v.z * look.z();
        if (z < 0.5) return null;
        double x = -(v.x * left.x() + v.y * left.y() + v.z * left.z()) / z;
        double y = (v.x * up.x() + v.y * up.y() + v.z * up.z()) / z;
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) w / h;
        return new float[]{(float) (w / 2.0 + x / (tanHalf * aspect) * w / 2.0), (float) (h / 2.0 - y / tanHalf * h / 2.0),
                (float) (h / 2.0 / (tanHalf * z))};
    }

    /**
     * Va por encima de toda la interfaz (mano, barra, inventario): durante el colapso la pantalla queda
     * negra y se dibuja encima solo la luz; en el estallido, blanco total.
     */
    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (NOVAS.isEmpty()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float[] s = screen(pt);
        int w = g.guiWidth(), h = g.guiHeight();
        if (s[1] > 0.01F) {
            g.fill(0, 0, w, h, ((int) (s[1] * 255) << 24) | 0x020006);
            g.flush();
            Nova n = NOVAS.get(NOVAS.size() - 1);
            float a = n.age + pt;
            float[] p = a < D ? project(n.core(a), w, h) : null;
            if (p != null) {
                float lightPx = Math.max(2.5F, n.coreSize(a) * p[2]);
                int lp = (int) (lightPx * 2.6F) + 5;
                float flick = 0.9F + 0.1F * Mth.sin(a * 1.7F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.setColor(1F, 0.96F, 1F, flick);
                RenderSystem.enableBlend();
                g.blit(LIGHT_TEX, (int) p[0] - lp, (int) p[1] - lp, lp * 2, lp * 2, 0, 0, 256, 256, 256, 256);
                g.setColor(1F, 1F, 1F, 1F);
                RenderSystem.disableBlend();
            }
        }
        if (s[0] > 0.01F) {
            g.fill(0, 0, w, h, ((int) (s[0] * 255) << 24) | 0xFFFCFF);
            g.flush();
        }
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (NOVAS.isEmpty()) return;
        float pt = (float) event.getPartialTick();
        Vec3 eye = event.getCamera().getPosition();
        float violet = 0F, white = 0F, black = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            float prox = Math.max(proximity(n, eye, a), 0.4F);
            if (a < R) violet += smooth(a / R) * 0.5F * prox;
            else if (a < C) {
                violet += 0.5F * prox * (1F - smooth((a - R) / 40F));
                white += 0.7F * smooth((a - R) / (float) (C - R)) * prox;
            } else if (a < D) {
                white += 0.7F * prox * (1F - smooth((a - C) / 20F));
                black += smooth((a - C) / 40F) * prox;
            } else white += (float) Math.exp(-(a - D) / 70.0) * prox;
        }
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        violet = Math.min(0.6F, violet); white = Math.min(1F, white); black = Math.min(0.95F, black);
        r = Mth.lerp(violet, r, 0.7F); g = Mth.lerp(violet, g, 0.45F); b = Mth.lerp(violet, b, 1F);
        r = Mth.lerp(black, r, 0.01F); g = Mth.lerp(black, g, 0F); b = Mth.lerp(black, b, 0.03F);
        r = Mth.lerp(white, r, 1F); g = Mth.lerp(white, g, 0.97F); b = Mth.lerp(white, b, 1F);
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (NOVAS.isEmpty()) return;
        Vec3 eye = event.getCamera().getPosition();
        float pt = (float) event.getPartialTick();
        float shake = 0F, time = 0F;
        for (Nova n : NOVAS) {
            float a = n.age + pt;
            time = a;
            if (a > C && a < D) shake += 0.4F * smooth((a - C) / (float) (D - C)); // vibra mientras colapsa
            double d = Math.sqrt(eye.distanceToSqr(n.x, n.groundY, n.z));
            float hit = a - D - (float) (d / SupernovaPayload.SHOCK_SPEED);
            if (hit >= 0) shake += (float) (11.0 * n.k / (1.0 + d / 160.0) * Math.exp(-hit / 70.0));
        }
        if (shake < 0.02F) return;
        shake = Math.min(shake, 11F);
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
                    size = (10F + 340F * (1F - (float) Math.exp(-t / 6F))) * n.k * sizeK;
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

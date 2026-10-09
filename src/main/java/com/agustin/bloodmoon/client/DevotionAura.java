package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientDevotion;
import com.agustin.bloodmoon.registry.ModParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/**
 * Aura de los devotos de la Luna de la Cosecha: una bruma de sangre que los envuelve. Con cada rango se espesa y se
 * agranda; desde Creyente (III) la recorren rayos de estática anaranjado oscuro, cada vez más largos y frecuentes.
 * En primera persona la propia aura se dibuja más tenue y solo de la cintura para abajo, para no tapar la vista.
 */
public final class DevotionAura {
    private static final int RANGE = 64;
    private static final Vector3f BLOOD_DARK = new Vector3f(0.20F, 0.0F, 0.012F);
    private static final Vector3f BLOOD = new Vector3f(0.62F, 0.03F, 0.05F);
    private static final Vector3f MIST_DARK = new Vector3f(0.13F, 0.0F, 0.01F);
    private static final Vector3f MIST = new Vector3f(0.32F, 0.01F, 0.02F);
    private static SpriteSet boltSprites;

    private DevotionAura() {}

    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DEVOTION_BOLT.get(), set -> {
            boltSprites = set;
            return (type, level, x, y, z, dx, dy, dz) -> null;
        });
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || ClientDevotion.AURAS.isEmpty()) return;
        Entity cam = mc.getCameraEntity();
        for (AbstractClientPlayer p : level.players()) {
            ClientDevotion.Aura aura = ClientDevotion.AURAS.get(p.getUUID());
            if (aura == null || p.isSpectator() || p.isInvisible() || !p.isAlive()) continue;
            if (cam != null && p.distanceToSqr(cam) > RANGE * RANGE) continue;
            boolean self = p == cam && mc.options.getCameraType().isFirstPerson();
            emit(mc, level, p, aura.level(), self);
        }
    }

    private static void emit(Minecraft mc, ClientLevel level, Player p, int rank, boolean self) {
        RandomSource r = level.random;
        float t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
        float radius = 0.45F + 0.85F * t;
        float yMax = self ? 0.9F : p.getBbHeight() * 1.05F;
        float k = self ? 0.3F : 1F;

        // motas de sangre que suben lento
        int n = count(r, (1.2F + 8.5F * t) * k);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = radius * (0.35 + 0.65 * Math.sqrt(r.nextDouble()));
            Vector3f c = new Vector3f(BLOOD_DARK).lerp(BLOOD, r.nextFloat());
            float scale = (0.7F + 1.3F * t) * (0.6F + 0.4F * r.nextFloat());
            level.addParticle(new DustParticleOptions(c, scale),
                    p.getX() + Math.cos(a) * d, p.getY() + r.nextFloat() * yMax, p.getZ() + Math.sin(a) * d,
                    0, 0.02 + 0.03 * t, 0);
        }
        // bruma espesa y oscura: lo que hace que el aura "pese"
        int m = count(r, (0.15F + 2.4F * t) * k);
        for (int i = 0; i < m; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = radius * (0.2 + 0.8 * r.nextDouble());
            Vector3f c = new Vector3f(MIST_DARK).lerp(MIST, r.nextFloat());
            level.addParticle(new DustParticleOptions(c, Math.min(4F, 2.2F + 1.8F * t)),
                    p.getX() + Math.cos(a) * d, p.getY() + r.nextFloat() * yMax * 0.85, p.getZ() + Math.sin(a) * d,
                    0, 0.01, 0);
        }
        // estática: rayos anaranjado oscuro desde Creyente
        if (rank >= 3 && boltSprites != null) {
            float chance = (0.03F + 0.22F * (rank - 3) / 8F) * (self ? 0.5F : 1F);
            int bolts = count(r, chance * (1F + t));
            for (int i = 0; i < bolts; i++) mc.particleEngine.add(new Bolt(level, p, radius, yMax, t, boltSprites));
        }
    }

    private static int count(RandomSource r, float f) {
        int n = (int) f;
        return n + (r.nextFloat() < f - n ? 1 : 0);
    }

    // ------------------------------------------------------------------ rayo de estática

    static final class Bolt extends TextureSheetParticle {
        private static final int LIGHT = 15728880;
        private final Player owner;
        private final float radius, yMax, t;
        private final int segs;
        private final float[] pts;

        Bolt(ClientLevel level, Player owner, float radius, float yMax, float t, SpriteSet sprites) {
            super(level, owner.getX(), owner.getY(), owner.getZ());
            this.owner = owner;
            this.radius = radius;
            this.yMax = yMax;
            this.t = t;
            this.hasPhysics = false;
            this.gravity = 0F;
            this.lifetime = 3 + this.random.nextInt(4);
            this.segs = 4 + this.random.nextInt(4);
            this.pts = new float[(segs + 1) * 3];
            this.pickSprite(sprites);
            this.setSize(4F, 4F);
            regen();
        }

        /** Nueva forma quebrada (la estática salta de lugar). */
        private void regen() {
            double a = random.nextDouble() * Math.PI * 2, d = radius * (0.4 + 0.6 * random.nextDouble());
            float x = (float) (Math.cos(a) * d), z = (float) (Math.sin(a) * d);
            float y = 0.1F + random.nextFloat() * Math.max(0.1F, yMax - 0.2F);
            // dirección: alrededor del cuerpo y algo vertical
            float dx = -z, dz = x, dy = (random.nextFloat() - 0.5F) * 1.6F * Math.max(0.3F, (float) d);
            float len = (0.45F + 1.1F * t) * (0.5F + 0.5F * random.nextFloat());
            float norm = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (norm < 1e-4F) norm = 1F;
            dx = dx / norm * len / segs; dy = dy / norm * len / segs; dz = dz / norm * len / segs;
            if (random.nextBoolean()) { dx = -dx; dz = -dz; }
            float jit = 0.10F * (1F + t);
            for (int i = 0; i <= segs; i++) {
                float j = (i == 0 || i == segs) ? 0F : 1F;
                pts[i * 3] = x + dx * i + (random.nextFloat() - 0.5F) * jit * j;
                pts[i * 3 + 1] = y + dy * i + (random.nextFloat() - 0.5F) * jit * j;
                pts[i * 3 + 2] = z + dz * i + (random.nextFloat() - 0.5F) * jit * j;
            }
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.lifetime || owner.isRemoved()) {
                this.remove();
                return;
            }
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            if (random.nextInt(3) == 0) regen();
        }

        @Override
        public void render(VertexConsumer buf, Camera camera, float pt) {
            Vec3 cam = camera.getPosition();
            float ox = (float) (Mth.lerp(pt, owner.xo, owner.getX()) - cam.x);
            float oy = (float) (Mth.lerp(pt, owner.yo, owner.getY()) - cam.y);
            float oz = (float) (Mth.lerp(pt, owner.zo, owner.getZ()) - cam.z);
            float fade = 1F - 0.5F * (age + pt) / lifetime;
            float flick = 0.55F + 0.45F * random.nextFloat();
            float glowW = 0.12F * (1F + 0.6F * t), coreW = 0.035F;
            for (int i = 0; i < segs; i++) {
                float ax = ox + pts[i * 3], ay = oy + pts[i * 3 + 1], az = oz + pts[i * 3 + 2];
                float bx = ox + pts[i * 3 + 3], by = oy + pts[i * 3 + 4], bz = oz + pts[i * 3 + 5];
                ribbon(buf, ax, ay, az, bx, by, bz, glowW, 0.62F, 0.22F, 0.02F, 0.45F * fade * flick);
                ribbon(buf, ax, ay, az, bx, by, bz, coreW, 0.98F, 0.52F, 0.14F, 0.95F * fade);
            }
        }

        /** Cinta de a hacia b que mira a la cámara (en coordenadas relativas a ella), de ambos lados. */
        private void ribbon(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz,
                            float w, float r, float g, float b, float alpha) {
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float mx = (ax + bx) * 0.5F, my = (ay + by) * 0.5F, mz = (az + bz) * 0.5F;
            // lado = d x vista
            float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6F) return;
            float h = w * 0.5F / sl;
            sx *= h; sy *= h; sz *= h;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            vert(buf, ax - sx, ay - sy, az - sz, u0, v1, r, g, b, alpha);
            vert(buf, ax + sx, ay + sy, az + sz, u1, v1, r, g, b, alpha);
            vert(buf, bx + sx, by + sy, bz + sz, u1, v0, r, g, b, alpha);
            vert(buf, bx - sx, by - sy, bz - sz, u0, v0, r, g, b, alpha);
            vert(buf, bx - sx, by - sy, bz - sz, u0, v0, r, g, b, alpha);
            vert(buf, bx + sx, by + sy, bz + sz, u1, v0, r, g, b, alpha);
            vert(buf, ax + sx, ay + sy, az + sz, u1, v1, r, g, b, alpha);
            vert(buf, ax - sx, ay - sy, az - sz, u0, v1, r, g, b, alpha);
        }

        private static void vert(VertexConsumer buf, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
            buf.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a).setLight(LIGHT);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LIGHT;
        }
    }
}

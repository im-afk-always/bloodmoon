package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.ClientDevotion;
import com.agustin.bloodmoon.registry.ModParticles;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Aura de los devotos de la Luna de la Cosecha: llamas translúcidas color sangre que nacen en los pies y suben.
 * En rangos bajos solo lamen los pies; con cada rango suben más alto hasta envolver a la persona entera (y un poco más).
 * Desde Creyente (III) las recorren rayos de estática rojos; con el rango ganan formas, ramas, largo y frecuencia.
 * En primera persona las propias llamas quedan bajas y más ralas, para no tapar la vista.
 */
public final class DevotionAura {
    private static final int RANGE = 64;
    private static SpriteSet boltSprites, flameSprites, moteSprites;
    /** Un anillo dorado persistente por devoto de la Providencia. */
    private static final java.util.Map<java.util.UUID, Halo> HALOS = new java.util.HashMap<>();

    /** Como PARTICLE_SHEET_TRANSLUCENT pero sin escribir profundidad: las llamas superpuestas no se recortan entre sí. */
    /** Aditivo y sin profundidad: los rayos suman luz roja sobre lo que tengan detrás. */
    static final ParticleRenderType GLOW = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "bloodmoon:devotion_glow";
        }
    };

    static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "bloodmoon:devotion_soft";
        }
    };

    private DevotionAura() {}

    public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.DEVOTION_BOLT.get(), set -> {
            boltSprites = set;
            return (type, level, x, y, z, dx, dy, dz) -> null;
        });
        event.registerSpriteSet(ModParticles.DEVOTION_MOTE.get(), set -> {
            moteSprites = set;
            return (type, level, x, y, z, dx, dy, dz) -> null;
        });
        event.registerSpriteSet(ModParticles.DEVOTION_FLAME.get(), set -> {
            flameSprites = set;
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
            if (aura.deity() == com.agustin.bloodmoon.Deity.PROVIDENCE) emitProvidence(mc, level, p, aura.level(), self);
            else emit(mc, level, p, aura.level(), self);
        }
    }

    /** Altura a la que llegan las llamas según el rango: ~0,35 (pies) en Iniciado, ~2,2 (sobre la cabeza) en Elegido. */
    static float flameHeight(int rank) {
        float t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
        return 0.35F + 1.85F * (float) Math.pow(t, 0.85);
    }

    private static void emit(Minecraft mc, ClientLevel level, Player p, int rank, boolean self) {
        RandomSource r = level.random;
        float t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
        float height = flameHeight(rank);
        if (self) height = Math.min(height, 0.55F);
        if (flameSprites != null) {
            int n = count(r, (2.2F + 5.5F * t) * (self ? 0.45F : 1F));
            for (int i = 0; i < n; i++) mc.particleEngine.add(new Flame(level, p, height, t, flameSprites));
        }
        if (rank >= 3 && boltSprites != null) {
            float yMax = self ? 0.9F : Math.min(p.getBbHeight() * 1.05F, height + 0.3F);
            float chance = (0.04F + 0.26F * (rank - 3) / 8F) * (self ? 0.5F : 1F);
            int bolts = count(r, chance * (1F + t));
            for (int i = 0; i < bolts; i++) mc.particleEngine.add(new Bolt(level, p, rank, yMax, self, boltSprites));
        }
    }

    // ------------------------------------------------------------------ Providencia

    private static void emitProvidence(Minecraft mc, ClientLevel level, Player p, int rank, boolean self) {
        if (boltSprites == null) return;
        Halo h = HALOS.get(p.getUUID());
        if (h == null || !h.alive(level)) {
            h = new Halo(level, p, boltSprites);
            HALOS.put(p.getUUID(), h);
            mc.particleEngine.add(h);
        }
        if (rank >= 3 && moteSprites != null) {
            RandomSource r = level.random;
            float t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
            float radius = Halo.radius(t);
            int n = count(r, (0.25F + 1.6F * t) * (self ? 0.4F : 1F));
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = radius * (0.3 + 0.7 * r.nextDouble());
                mc.particleEngine.add(new Mote(level, p.getX() + Math.cos(a) * d, p.getY() + 0.1 + r.nextFloat() * p.getBbHeight(),
                        p.getZ() + Math.sin(a) * d, moteSprites, t));
            }
        }
    }

    /** Cinta de a hacia b mirando a la cámara (coordenadas relativas a ella), de ambos lados. */
    static void ribbon(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz, float w,
                       float r, float g, float b, float alpha, float u0, float u1, float v0, float v1) {
        if (alpha <= 0.003F) return;
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5F, my = (ay + by) * 0.5F, mz = (az + bz) * 0.5F;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6F) return;
        float h = w * 0.5F / sl;
        sx *= h; sy *= h; sz *= h;
        int L = 15728880;
        buf.addVertex(ax - sx, ay - sy, az - sz).setUv(u0, v1).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(ax + sx, ay + sy, az + sz).setUv(u1, v1).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(bx + sx, by + sy, bz + sz).setUv(u1, v0).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(bx - sx, by - sy, bz - sz).setUv(u0, v0).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(bx - sx, by - sy, bz - sz).setUv(u0, v0).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(bx + sx, by + sy, bz + sz).setUv(u1, v0).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(ax + sx, ay + sy, az + sz).setUv(u1, v1).setColor(r, g, b, alpha).setLight(L);
        buf.addVertex(ax - sx, ay - sy, az - sz).setUv(u0, v1).setColor(r, g, b, alpha).setLight(L);
    }

    /**
     * Halo de la Providencia: un anillo de luz dorada que rodea al jugador a la altura de la cintura, gira despacio y lo
     * recorren tres cuentas brillantes. Crece con el rango:
     * <ul>
     *   <li>I: un anillo fino y tenue.</li>
     *   <li>III: más brillo; motas de luz que suben alrededor.</li>
     *   <li>V: aparece una aureola sobre la cabeza.</li>
     *   <li>VII: un segundo anillo, inclinado, que gira al revés (giroscopio).</li>
     *   <li>IX: un anillo de luz en el suelo y columnas de luz que suben desde él.</li>
     *   <li>XI: un tercer anillo, inclinado hacia el otro lado.</li>
     * </ul>
     */
    static final class Halo extends TextureSheetParticle {
        private final Player owner;
        private final ClientLevel lvl;
        private int rank = 1;
        private long lastTick;

        Halo(ClientLevel level, Player owner, SpriteSet sprites) {
            super(level, owner.getX(), owner.getY(), owner.getZ());
            this.owner = owner;
            this.lvl = level;
            this.hasPhysics = false;
            this.gravity = 0F;
            this.lifetime = Integer.MAX_VALUE;
            this.pickSprite(sprites);
            this.setSize(3.5F, 3.5F);
            this.lastTick = level.getGameTime();
        }

        static float radius(float t) {
            return 0.5F + 0.25F * t;
        }

        /** Sigue vivo en este mundo (si el motor de partículas lo descartó, deja de recibir ticks). */
        boolean alive(ClientLevel level) {
            return isAlive() && lvl == level && level.getGameTime() - lastTick <= 3;
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            lastTick = lvl.getGameTime();
            ClientDevotion.Aura a = ClientDevotion.AURAS.get(owner.getUUID());
            Entity cam = Minecraft.getInstance().getCameraEntity();
            if (owner.isRemoved() || a == null || a.deity() != com.agustin.bloodmoon.Deity.PROVIDENCE || owner.isInvisible()
                    || owner.isSpectator() || (cam != null && owner.distanceToSqr(cam) > (RANGE + 8) * (RANGE + 8))) {
                this.remove();
                return;
            }
            rank = a.level();
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
        }

        @Override
        public void render(VertexConsumer buf, Camera camera, float pt) {
            Minecraft mc = Minecraft.getInstance();
            boolean self = owner == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            Vec3 cam = camera.getPosition();
            float ox = (float) (Mth.lerp(pt, owner.xo, owner.getX()) - cam.x);
            float oy = (float) (Mth.lerp(pt, owner.yo, owner.getY()) - cam.y);
            float oz = (float) (Mth.lerp(pt, owner.zo, owner.getZ()) - cam.z);
            float time = lvl.getGameTime() + pt;
            float t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
            float R = radius(t), h = owner.getBbHeight();
            float w = 1F + 0.8F * t, a = 0.45F + 0.55F * t;
            float pulse = 0.9F + 0.1F * Mth.sin(time * 0.06F);
            float yRing = h * 0.52F + 0.04F * Mth.sin(time * 0.05F);

            ring(buf, ox, oy + yRing, oz, R, 0F, 0F, time * 0.03F, time, w, a * pulse);
            if (rank >= 5 && !self) ring(buf, ox, oy + h + 0.16F + 0.02F * Mth.sin(time * 0.07F), oz, 0.26F, 0F, 0F, -time * 0.05F, time, 0.8F, a);
            if (rank >= 7) ring(buf, ox, oy + yRing, oz, R * 1.08F, 0.5F, time * 0.012F, -time * 0.04F, time, w * 0.8F, a * 0.7F);
            if (rank >= 11) ring(buf, ox, oy + yRing, oz, R * 1.16F, -0.55F, -time * 0.009F, time * 0.035F, time, w * 0.7F, a * 0.6F);
            if (rank >= 9) {
                float gR = 0.85F + 0.2F * t;
                ring(buf, ox, oy + 0.04F, oz, gR, 0F, 0F, time * 0.02F, time, w * 0.9F, a * 0.6F);
                if (!self) pillars(buf, ox, oy, oz, gR * 0.85F, h + 0.5F, time, a);
            }
        }

        /** Anillo inclinado {@code tilt} (eje X) y girado {@code yaw} (eje Y); {@code spin} hace correr las cuentas. */
        private void ring(VertexConsumer buf, float cx, float cy, float cz, float R, float tilt, float yaw, float spin,
                          float time, float w, float a) {
            int n = 40;
            float ct = Mth.cos(tilt), st = Mth.sin(tilt), cy0 = Mth.cos(yaw), sy0 = Mth.sin(yaw);
            float[] px = new float[n + 1], py = new float[n + 1], pz = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float th = Mth.TWO_PI * i / n;
                float x = Mth.cos(th) * R, z = Mth.sin(th) * R;
                float y = -z * st;
                z = z * ct;
                px[i] = cx + x * cy0 - z * sy0;
                py[i] = cy + y;
                pz[i] = cz + x * sy0 + z * cy0;
            }
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            for (int i = 0; i < n; i++) {
                float th = Mth.TWO_PI * (i + 0.5F) / n;
                float bead = (float) Math.pow(0.5F + 0.5F * Mth.cos(3F * th - spin * 2.2F), 6);
                float b = 0.55F + 0.45F * bead;
                ribbon(buf, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.22F * w, 1F, 0.62F, 0.16F, 0.16F * a, u0, u1, v0, v1);
                ribbon(buf, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.09F * w, 1F, 0.78F, 0.3F, 0.45F * a * b, u0, u1, v0, v1);
                ribbon(buf, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], 0.028F * w, 1F, 0.96F, 0.78F, 0.9F * a * b, u0, u1, v0, v1);
            }
        }

        /** Columnas de luz que suben desde el anillo del suelo y se desvanecen hacia arriba. */
        private void pillars(VertexConsumer buf, float cx, float cy, float cz, float R, float top, float time, float a) {
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int n = 6, seg = 5;
            for (int i = 0; i < n; i++) {
                float th = Mth.TWO_PI * i / n + time * 0.01F;
                float x = cx + Mth.cos(th) * R, z = cz + Mth.sin(th) * R;
                float flick = 0.7F + 0.3F * Mth.sin(time * 0.11F + i * 1.7F);
                for (int s = 0; s < seg; s++) {
                    float y0 = cy + top * s / seg, y1 = cy + top * (s + 1) / seg;
                    float fade = 1F - (s + 0.5F) / seg;
                    ribbon(buf, x, y0, z, x, y1, z, 0.18F, 1F, 0.75F, 0.3F, 0.14F * a * fade * flick, u0, u1, v0, v1);
                }
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return GLOW;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880;
        }
    }

    /** Mota de luz dorada que sube despacio, titila y se apaga. */
    static final class Mote extends TextureSheetParticle {
        private final float baseSize, phase;

        Mote(ClientLevel level, double x, double y, double z, SpriteSet sprites, float t) {
            super(level, x, y, z);
            this.hasPhysics = false;
            this.gravity = 0F;
            this.friction = 0.96F;
            this.xd = (this.random.nextFloat() - 0.5F) * 0.01F;
            this.zd = (this.random.nextFloat() - 0.5F) * 0.01F;
            this.yd = 0.008F + 0.014F * this.random.nextFloat();
            this.lifetime = 25 + this.random.nextInt(25);
            this.baseSize = (0.035F + 0.04F * this.random.nextFloat()) * (1F + 0.4F * t);
            this.phase = this.random.nextFloat() * 6.28F;
            this.quadSize = baseSize;
            this.pickSprite(sprites);
            float w = this.random.nextFloat();
            this.setColor(1F, Mth.lerp(w, 0.7F, 0.92F), Mth.lerp(w, 0.25F, 0.6F));
            this.setAlpha(0F);
        }

        @Override
        public void tick() {
            super.tick();
            float life = age / (float) lifetime;
            float in = Mth.clamp(life / 0.2F, 0F, 1F), out = Mth.clamp((1F - life) / 0.4F, 0F, 1F);
            this.setAlpha(0.9F * in * out * (0.7F + 0.3F * Mth.sin(age * 0.5F + phase)));
            this.quadSize = baseSize * (0.8F + 0.2F * Mth.sin(age * 0.3F + phase));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return GLOW;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 15728880;
        }
    }

    private static int count(RandomSource r, float f) {
        int n = (int) f;
        return n + (r.nextFloat() < f - n ? 1 : 0);
    }

    // ------------------------------------------------------------------ llama

    /** Lengua de fuego pegada al jugador: nace en un anillo alrededor de los pies, sube, se cierra hacia el cuerpo y se apaga. */
    static final class Flame extends TextureSheetParticle {
        private static final int LIGHT = 15728880;
        private final Player owner;
        private final SpriteSet sprites;
        private final float height, size0, sway, swayPhase;
        private float ox, oy, oz, pox, poy, poz;

        Flame(ClientLevel level, Player owner, float height, float t, SpriteSet sprites) {
            super(level, owner.getX(), owner.getY(), owner.getZ());
            this.owner = owner;
            this.sprites = sprites;
            this.hasPhysics = false;
            this.gravity = 0F;
            this.lifetime = 9 + this.random.nextInt(7);
            double a = this.random.nextDouble() * Math.PI * 2;
            float ring = 0.18F + 0.22F * this.random.nextFloat();
            this.ox = (float) Math.cos(a) * ring;
            this.oz = (float) Math.sin(a) * ring;
            this.oy = 0.02F;
            // unas llamas llegan al tope, otras se quedan a mitad de camino
            this.height = height * (0.55F + 0.45F * this.random.nextFloat());
            this.size0 = (0.30F + 0.28F * t) * (0.75F + 0.5F * this.random.nextFloat()) * Math.min(1F, 0.5F + height);
            this.sway = 0.02F + 0.03F * this.random.nextFloat();
            this.swayPhase = this.random.nextFloat() * 6.28F;
            float shade = this.random.nextFloat();
            this.setColor(Mth.lerp(shade, 0.55F, 0.92F), Mth.lerp(shade, 0.02F, 0.10F), Mth.lerp(shade, 0.03F, 0.08F));
            this.pox = ox; this.poy = oy; this.poz = oz;
            this.setSize(1.5F, 3F);
            this.setSpriteFromAge(sprites);
            this.setAlpha(0F);
        }

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            pox = ox; poy = oy; poz = oz;
            if (this.age++ >= this.lifetime || owner.isRemoved()) {
                this.remove();
                return;
            }
            float life = age / (float) lifetime;
            oy += height / lifetime;
            // se cierra hacia el cuerpo al subir y ondula
            float pull = 0.92F;
            ox = ox * pull + Mth.sin(age * 0.9F + swayPhase) * sway;
            oz = oz * pull + Mth.cos(age * 0.8F + swayPhase) * sway;
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            this.setSpriteFromAge(sprites);
            float in = Mth.clamp(life / 0.15F, 0F, 1F), out = Mth.clamp((1F - life) / 0.45F, 0F, 1F);
            this.setAlpha(0.62F * in * out);
            this.quadSize = size0 * (0.75F + 0.5F * Mth.sin(Mth.PI * Math.min(1F, life * 1.3F)));
        }

        /** Siempre vertical: gira solo alrededor del eje Y para mirar a la cámara. */
        @Override
        public void render(VertexConsumer buf, Camera camera, float pt) {
            Vec3 cam = camera.getPosition();
            float cx = (float) (Mth.lerp(pt, owner.xo, owner.getX()) - cam.x) + Mth.lerp(pt, pox, ox);
            float cy = (float) (Mth.lerp(pt, owner.yo, owner.getY()) - cam.y) + Mth.lerp(pt, poy, oy);
            float cz = (float) (Mth.lerp(pt, owner.zo, owner.getZ()) - cam.z) + Mth.lerp(pt, poz, oz);
            float hl = Mth.sqrt(cx * cx + cz * cz);
            float rx, rz;
            if (hl < 1e-4F) { rx = 1F; rz = 0F; } else { rx = -cz / hl; rz = cx / hl; }
            float s = getQuadSize(pt);
            float w = s * 0.75F, h = s * 1.6F;
            float x0 = cx - rx * w, z0 = cz - rz * w, x1 = cx + rx * w, z1 = cz + rz * w;
            float yb = cy - h * 0.25F, yt = cy + h * 0.75F;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            float r = rCol, g = gCol, b = bCol, a = alpha;
            buf.addVertex(x0, yb, z0).setUv(u1, v1).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x0, yt, z0).setUv(u1, v0).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x1, yt, z1).setUv(u0, v0).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x1, yb, z1).setUv(u0, v1).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x1, yb, z1).setUv(u0, v1).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x1, yt, z1).setUv(u0, v0).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x0, yt, z0).setUv(u1, v0).setColor(r, g, b, a).setLight(LIGHT);
            buf.addVertex(x0, yb, z0).setUv(u1, v1).setColor(r, g, b, a).setLight(LIGHT);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return SOFT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LIGHT;
        }
    }

    // ------------------------------------------------------------------ rayo de estática

    /**
     * Rayo de estática rojo. Cuatro formas, que se van habilitando con el rango:
     * <ul>
     *   <li>ARCO (III+): salta entre dos puntos del cuerpo, curvado hacia afuera.</li>
     *   <li>DESCARGA (V+): sale del cuerpo hacia afuera y se bifurca en la punta.</li>
     *   <li>REPTANTE (VII+): corre por el suelo desde los pies, muy ramificado.</li>
     *   <li>ESPIRAL (IX+): se enrosca alrededor del cuerpo, subiendo.</li>
     * </ul>
     * El trazo es un fractal (desplazamiento de punto medio) con ramas y sub-ramas; cada tick puede volver a
     * quebrarse manteniendo los extremos, titila y a veces se apaga un instante. Se dibuja en tres capas aditivas:
     * bruma granate, resplandor rojo y núcleo rojo claro.
     */
    static final class Bolt extends TextureSheetParticle {
        private static final int LIGHT = 15728880;
        private static final int ARC = 0, SPIKE = 1, CRAWL = 2, COIL = 3;
        private final Player owner;
        private final int kind, levels;
        private final float t, yMax, body;
        /** Puntos de control fijos del rayo (relativos a los pies del jugador). */
        private final float[] anchors;
        private final java.util.List<Strand> strands = new java.util.ArrayList<>();
        private boolean dark;

        private record Strand(float[] pts, float width, float bright) {}

        Bolt(ClientLevel level, Player owner, int rank, float yMax, boolean self, SpriteSet sprites) {
            super(level, owner.getX(), owner.getY(), owner.getZ());
            this.owner = owner;
            this.t = Mth.clamp((rank - 1) / 10F, 0F, 1F);
            this.yMax = yMax;
            this.body = 0.36F + 0.22F * t;
            int kinds = rank >= 9 ? 4 : rank >= 7 ? 3 : rank >= 5 ? 2 : 1;
            int k = this.random.nextInt(kinds);
            if (self && k == COIL) k = ARC;
            this.kind = k;
            this.levels = 3 + (rank >= 6 ? 1 : 0) + (this.random.nextFloat() < t ? 1 : 0);
            this.hasPhysics = false;
            this.gravity = 0F;
            this.lifetime = 4 + this.random.nextInt(4 + (int) (4 * t));
            this.pickSprite(sprites);
            this.setSize(5F, 4F);
            this.anchors = makeAnchors();
            regen();
        }

        // ---------------------------------------------------------- forma

        private float rnd(float a, float b) { return a + (b - a) * random.nextFloat(); }

        private float[] onBody(double ang, float y, float rad) {
            return new float[]{(float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad};
        }

        /** Puntos de control: para ARCO/DESCARGA/REPTANTE, inicio-medio-fin; para ESPIRAL, la hélice entera. */
        private float[] makeAnchors() {
            double a0 = random.nextDouble() * Math.PI * 2;
            float hi = Math.max(0.3F, yMax);
            switch (kind) {
                case SPIKE -> {
                    float y = rnd(0.2F, hi * 0.9F);
                    float[] s = onBody(a0, y, body * 0.8F);
                    float len = rnd(0.6F, 1.0F) * (0.7F + 1.1F * t);
                    double out = a0 + rnd(-0.5F, 0.5F);
                    float[] e = onBody(out, y + rnd(-0.2F, 0.6F) * len, body + len);
                    float[] m = mid(s, e, 0F);
                    return cat(s, m, e);
                }
                case CRAWL -> {
                    float[] s = onBody(a0, 0.05F, body * 0.7F);
                    float len = rnd(0.7F, 1.1F) * (0.5F + 0.9F * t);
                    double out = a0 + rnd(-0.7F, 0.7F);
                    float[] e = onBody(out, 0.03F, body + len);
                    float[] m = onBody((a0 + out) / 2 + rnd(-0.3F, 0.3F), 0.05F, body + len * 0.5F);
                    return cat(s, m, e);
                }
                case COIL -> {
                    int n = 7;
                    float turns = rnd(0.6F, 1.1F) * (random.nextBoolean() ? 1 : -1);
                    float y0 = rnd(0.05F, hi * 0.3F), y1 = Math.min(hi, y0 + rnd(0.6F, 1.3F) * (0.6F + 0.6F * t));
                    float[] pts = new float[n * 3];
                    for (int i = 0; i < n; i++) {
                        float f = i / (float) (n - 1);
                        float[] q = onBody(a0 + f * turns * Math.PI * 2, Mth.lerp(f, y0, y1), body * rnd(0.95F, 1.15F));
                        System.arraycopy(q, 0, pts, i * 3, 3);
                    }
                    return pts;
                }
                default -> { // ARC
                    float y1 = rnd(0.1F, hi), y2 = rnd(0.1F, hi);
                    double a1 = a0 + rnd(0.7F, 2.6F) * (random.nextBoolean() ? 1 : -1);
                    float[] s = onBody(a0, y1, body * 0.9F), e = onBody(a1, y2, body * 0.9F);
                    // el medio se curva hacia afuera
                    float[] m = onBody((a0 + a1) / 2, (y1 + y2) / 2 + rnd(-0.1F, 0.25F), body + rnd(0.1F, 0.3F) * (0.6F + 0.6F * t));
                    return cat(s, m, e);
                }
            }
        }

        /** Vuelve a quebrar el trazo (los puntos de control quedan). */
        private void regen() {
            strands.clear();
            float rough = kind == COIL ? 0.16F : 0.28F;
            float[] main = fractal(anchors, levels, rough);
            float w = 1F + 0.5F * t;
            strands.add(new Strand(main, w, 1F));
            // ramas y sub-ramas
            float expected = (1.5F + 2.5F * t) * (kind == CRAWL ? 1.4F : 1F);
            branches(main, w * 0.6F, 0.75F, expected, 2);
        }

        /** {@code expected}: cantidad media de ramas que salen de este trazo. */
        private void branches(float[] pts, float w, float bright, float expected, int depth) {
            int n = pts.length / 3;
            float prob = expected / Math.max(1, n - 2);
            float total = length(pts);
            for (int i = 1; i < n - 1; i++) {
                if (random.nextFloat() >= prob) continue;
                float dx = pts[i * 3 + 3] - pts[i * 3], dy = pts[i * 3 + 4] - pts[i * 3 + 1], dz = pts[i * 3 + 5] - pts[i * 3 + 2];
                float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl < 1e-4F) continue;
                float len = total * rnd(0.18F, 0.42F);
                // dirección: la del trazo, desviada
                float bx = dx / dl + rnd(-0.9F, 0.9F), by = dy / dl + rnd(-0.6F, 0.6F), bz = dz / dl + rnd(-0.9F, 0.9F);
                if (kind == CRAWL) by = rnd(-0.05F, 0.1F);
                float bl = Mth.sqrt(bx * bx + by * by + bz * bz);
                if (bl < 1e-4F) continue;
                float[] s = {pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2]};
                float[] e = {s[0] + bx / bl * len, Math.max(0.01F, s[1] + by / bl * len), s[2] + bz / bl * len};
                float[] b = fractal(cat(s, e), Math.max(1, levels - 2), 0.3F);
                strands.add(new Strand(b, w, bright));
                if (depth > 1) branches(b, w * 0.6F, bright * 0.75F, 0.6F, depth - 1);
            }
        }

        /** Desplazamiento de punto medio entre puntos de control consecutivos. */
        private float[] fractal(float[] ctrl, int lv, float rough) {
            float[] pts = ctrl;
            for (int l = 0; l < lv; l++) {
                int n = pts.length / 3;
                float[] out = new float[(n * 2 - 1) * 3];
                for (int i = 0; i < n - 1; i++) {
                    float ax = pts[i * 3], ay = pts[i * 3 + 1], az = pts[i * 3 + 2];
                    float bx = pts[i * 3 + 3], by = pts[i * 3 + 4], bz = pts[i * 3 + 5];
                    float dx = bx - ax, dy = by - ay, dz = bz - az;
                    float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                    // desvío perpendicular aleatorio
                    float rx = random.nextFloat() - 0.5F, ry = random.nextFloat() - 0.5F, rz = random.nextFloat() - 0.5F;
                    if (len > 1e-4F) {
                        float dot = (rx * dx + ry * dy + rz * dz) / (len * len);
                        rx -= dx * dot; ry -= dy * dot; rz -= dz * dot;
                    }
                    float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
                    float k = rl < 1e-4F ? 0F : len * rough * rnd(0.3F, 1F) / rl;
                    out[i * 6] = ax; out[i * 6 + 1] = ay; out[i * 6 + 2] = az;
                    out[i * 6 + 3] = (ax + bx) * 0.5F + rx * k;
                    out[i * 6 + 4] = Math.max(0.01F, (ay + by) * 0.5F + ry * k * (kind == CRAWL ? 0.25F : 1F));
                    out[i * 6 + 5] = (az + bz) * 0.5F + rz * k;
                }
                System.arraycopy(pts, (n - 1) * 3, out, (n * 2 - 2) * 3, 3);
                pts = out;
            }
            return pts;
        }

        private static float length(float[] pts) {
            float s = 0;
            for (int i = 0; i + 5 < pts.length; i += 3) {
                float dx = pts[i + 3] - pts[i], dy = pts[i + 4] - pts[i + 1], dz = pts[i + 5] - pts[i + 2];
                s += Mth.sqrt(dx * dx + dy * dy + dz * dz);
            }
            return s;
        }

        private static float[] mid(float[] a, float[] b, float lift) {
            return new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2 + lift, (a[2] + b[2]) / 2};
        }

        private static float[] cat(float[]... parts) {
            int n = 0;
            for (float[] p : parts) n += p.length;
            float[] out = new float[n];
            int o = 0;
            for (float[] p : parts) { System.arraycopy(p, 0, out, o, p.length); o += p.length; }
            return out;
        }

        // ---------------------------------------------------------- vida

        @Override
        public void tick() {
            this.xo = this.x; this.yo = this.y; this.zo = this.z;
            if (this.age++ >= this.lifetime || owner.isRemoved()) {
                this.remove();
                return;
            }
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            if (random.nextFloat() < 0.55F) regen();
            dark = age > 1 && random.nextFloat() < 0.15F;   // se apaga un instante
        }

        @Override
        public void render(VertexConsumer buf, Camera camera, float pt) {
            if (dark) return;
            Vec3 cam = camera.getPosition();
            float ox = (float) (Mth.lerp(pt, owner.xo, owner.getX()) - cam.x);
            float oy = (float) (Mth.lerp(pt, owner.yo, owner.getY()) - cam.y);
            float oz = (float) (Mth.lerp(pt, owner.zo, owner.getZ()) - cam.z);
            float life = (age + pt) / lifetime;
            float fade = life < 0.15F ? life / 0.15F : 1F - 0.6F * Math.max(0F, life - 0.4F) / 0.6F;
            float flick = 0.5F + 0.5F * random.nextFloat();
            for (Strand st : strands) {
                float[] p = st.pts();
                int n = p.length / 3;
                float b = st.bright() * fade;
                for (int i = 0; i < n - 1; i++) {
                    float taper = 1F - 0.55F * i / (float) (n - 1);
                    float w = st.width() * taper;
                    float ax = ox + p[i * 3], ay = oy + p[i * 3 + 1], az = oz + p[i * 3 + 2];
                    float bx = ox + p[i * 3 + 3], by = oy + p[i * 3 + 4], bz = oz + p[i * 3 + 5];
                    ribbon(buf, ax, ay, az, bx, by, bz, 0.26F * w, 0.45F, 0.0F, 0.02F, 0.22F * b * flick);   // bruma granate
                    ribbon(buf, ax, ay, az, bx, by, bz, 0.10F * w, 0.95F, 0.05F, 0.04F, 0.60F * b * flick);  // resplandor rojo
                    ribbon(buf, ax, ay, az, bx, by, bz, 0.03F * w, 1.0F, 0.50F, 0.45F, 0.95F * b);            // núcleo
                }
            }
        }

        /** Cinta de a hacia b que mira a la cámara (en coordenadas relativas a ella), de ambos lados. */
        private void ribbon(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz,
                            float w, float r, float g, float b, float alpha) {
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float mx = (ax + bx) * 0.5F, my = (ay + by) * 0.5F, mz = (az + bz) * 0.5F;
            float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6F) return;
            float h = w * 0.5F / sl;
            sx *= h; sy *= h; sz *= h;
            // alargar un poco cada tramo para tapar las juntas
            float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (dl > 1e-5F) {
                float e = w * 0.35F / dl;
                ax -= dx * e; ay -= dy * e; az -= dz * e;
                bx += dx * e; by += dy * e; bz += dz * e;
            }
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
            return GLOW;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LIGHT;
        }
    }
}

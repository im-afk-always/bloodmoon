package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.VoidSkeleton;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asaltos del Dominio (desde la fase Conquista). Cada uno o dos días, a un jugador que esté dentro del radio se le abre
 * una Puerta de Guerra a ~26 bloques, del lado del coliseo, y salen oleadas de tropas que lo buscan y rompen lo que se
 * interponga.
 * <ul>
 *   <li>Rechazado (todas las tropas muertas): el Dominio pierde esencia y la puerta se derrumba.</li>
 *   <li>Fallido (5 minutos sin rechazarlo, o el jugador muere): el Dominio clava una cabeza de playa: los 3×3 chunks
 *   alrededor de la puerta mueren y nace un obelisco.</li>
 * </ul>
 */
public final class InvasionRaids {
    private static final long DURATION = 6000L;
    private static final Map<UUID, Raid> RAIDS = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    private InvasionRaids() {}

    private static final class Raid {
        UUID player;
        int faction;
        BlockPos gate;
        List<BlockPos> gateBlocks = new ArrayList<>();
        final List<UUID> troops = new ArrayList<>();
        int toSpawn;
        long start, lastSpawn;
    }

    public static void clear() {
        RAIDS.clear();
        NEXT.clear();
    }

    /** Puertas abiertas (para el mapa). */
    public static List<BlockPos> gates() {
        List<BlockPos> out = new ArrayList<>();
        for (Raid r : RAIDS.values()) out.add(r.gate);
        return out;
    }

    private static long delay(Faction f, RandomSource r) {
        double speed = Math.max(0.1, BloodMoonConfig.INVASION_SPEED.get());
        long base = f.phase >= 3 ? 24000 : 36000;
        return (long) ((base + r.nextInt((int) base)) / speed);
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) return;
        InvasionData data = InvasionData.get(level);
        long now = level.getGameTime();
        RandomSource r = level.random;
        // nuevos asaltos
        for (Faction f : data.factions) {
            if (!f.active || f.phase < 2) continue;
            double rad = InvasionManager.radius();
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || RAIDS.containsKey(p.getUUID())) continue;
                double dx = p.getX() - f.center.getX(), dz = p.getZ() - f.center.getZ();
                if (dx * dx + dz * dz > rad * rad) continue;
                long next = NEXT.computeIfAbsent(p.getUUID(), u -> now + delay(f, r));
                if (now >= next) start(level, f, p);
            }
        }
        // en curso
        for (Iterator<Raid> it = RAIDS.values().iterator(); it.hasNext(); ) {
            Raid raid = it.next();
            if (update(level, data, raid, now)) it.remove();
        }
    }

    /** Para pruebas (OP): asalto ya contra este jugador, del Dominio activo más cercano. */
    public static boolean force(ServerLevel level, ServerPlayer p) {
        if (RAIDS.containsKey(p.getUUID())) return false;
        Faction f = InvasionManager.nearest(level, p.blockPosition());
        if (f == null || !f.active) return false;
        start(level, f, p);
        return RAIDS.containsKey(p.getUUID());
    }

    private static void start(ServerLevel level, Faction f, ServerPlayer p) {
        double dx = f.center.getX() - p.getX(), dz = f.center.getZ() - p.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        BlockPos gate = null;
        for (int tries = 0; tries < 5 && gate == null; tries++) {
            double a = Math.atan2(dz, dx) + (tries == 0 ? 0 : (tries % 2 == 0 ? 1 : -1) * 0.5 * ((tries + 1) / 2));
            int gx = (int) Math.round(p.getX() + Math.cos(a) * 26), gz = (int) Math.round(p.getZ() + Math.sin(a) * 26);
            if (level.getChunkSource().getChunkNow(gx >> 4, gz >> 4) == null) continue;
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            if (!level.getFluidState(new BlockPos(gx, gy - 1, gz)).isEmpty()) continue;
            gate = new BlockPos(gx, gy, gz);
        }
        if (gate == null) {
            NEXT.put(p.getUUID(), level.getGameTime() + 1200);   // reintenta en un minuto
            return;
        }
        boolean alongX = Math.abs(dx) < Math.abs(dz);   // el arco mira al jugador
        Raid raid = new Raid();
        raid.player = p.getUUID();
        raid.faction = f.id;
        raid.gate = gate;
        raid.start = level.getGameTime();
        int others = 0;
        for (ServerPlayer q : level.players()) if (q != p && q.distanceToSqr(p) < 64 * 64) others++;
        raid.toSpawn = 6 + 3 * f.phase + 2 * others;
        for (DominionTerraform.Placement pl : DominionStructures.gate(level, gate.getX(), gate.getZ(), alongX)) {
            level.setBlock(pl.pos(), pl.state(), 3);
            raid.gateBlocks.add(pl.pos());
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, gate.getX() + 0.5, gate.getY() + 4, gate.getZ() + 0.5, 300, 2, 4, 2, 0.2);
        RAIDS.put(p.getUUID(), raid);
        for (ServerPlayer q : level.players()) {
            if (q.distanceToSqr(gate.getX(), gate.getY(), gate.getZ()) > 96 * 96) continue;
            q.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            q.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("bloodmoon.invasion.raid.sub", f.name).withStyle(ChatFormatting.LIGHT_PURPLE)));
            q.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("bloodmoon.invasion.raid.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
            q.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2F, 0.6F);
        }
    }

    /** Devuelve true cuando el asalto terminó. */
    private static boolean update(ServerLevel level, InvasionData data, Raid raid, long now) {
        Faction f = data.faction(raid.faction);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(raid.player);
        raid.troops.removeIf(u -> {
            Entity e = level.getEntity(u);
            return e == null || !e.isAlive();
        });
        boolean gone = p == null || p.level().dimension() != Level.OVERWORLD
                || p.distanceToSqr(raid.gate.getX(), raid.gate.getY(), raid.gate.getZ()) > 160 * 160;
        if (f == null || !f.active || gone) {   // se canceló: el jugador se fue o el Dominio cayó
            dismiss(level, raid);
            crumble(level, raid);
            NEXT.put(raid.player, now + 12000);
            return true;
        }
        if (p.isDeadOrDying() || now - raid.start > DURATION) {
            conquer(level, data, f, raid);
            return true;
        }
        if (raid.toSpawn > 0 && now - raid.lastSpawn >= 40) {
            int n = Math.min(raid.toSpawn, 1 + level.random.nextInt(2));
            for (int i = 0; i < n; i++) spawnRaider(level, raid, p);
            raid.toSpawn -= n;
            raid.lastSpawn = now;
        }
        if (raid.toSpawn == 0 && raid.troops.isEmpty()) {   // rechazado
            f.essence = Math.max(0, f.essence - 150);
            data.setDirty();
            crumble(level, raid);
            NEXT.put(raid.player, now + (long) (delay(f, level.random) * 1.3));
            Component msg = Component.translatable("bloodmoon.invasion.raid.repelled", f.name).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer q : level.players()) {
                if (q.distanceToSqr(raid.gate.getX(), raid.gate.getY(), raid.gate.getZ()) < 128 * 128) {
                    q.sendSystemMessage(msg);
                    q.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 1F);
                }
            }
            return true;
        }
        return false;
    }

    private static void spawnRaider(ServerLevel level, Raid raid, ServerPlayer target) {
        boolean archer = level.random.nextFloat() < 0.35F;
        VoidSkeleton s = (archer ? ModEntities.VOID_ARCHER.get() : ModEntities.VOID_SENTINEL.get()).create(level);
        if (s == null) return;
        BlockPos g = raid.gate;
        s.moveTo(g.getX() + 0.5 + level.random.nextGaussian() * 0.6, g.getY() + 1, g.getZ() + 0.5 + level.random.nextGaussian() * 0.6,
                level.random.nextFloat() * 360F, 0F);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(g), MobSpawnType.EVENT, null);
        s.bindToDominion();
        s.setRaider(true);
        s.setTarget(target);
        level.addFreshEntity(s);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, s.getX(), s.getY() + 1, s.getZ(), 25, 0.3, 0.8, 0.3, 0.05);
        level.playSound(null, g, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1F, 0.5F);
        raid.troops.add(s.getUUID());
    }

    /** Cabeza de playa: los 3×3 chunks alrededor de la puerta mueren y nace un obelisco. */
    private static void conquer(ServerLevel level, InvasionData data, Faction f, Raid raid) {
        ChunkPos gc = new ChunkPos(raid.gate);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long k = ChunkPos.asLong(gc.x + dx, gc.z + dz);
            InvasionData.Cell c = InvasionManager.claim(data, k, f);
            if (c == null) continue;
            c.influence = 100;
            if (dx == 0 && dz == 0 && c.structure == DominionStructures.NONE) {
                c.obelisk = true;
                c.obeliskBuilt = false;
            }
            DominionTerraform.enqueue(k);
        }
        data.setDirty();
        dismiss(level, raid);
        Component msg = Component.translatable("bloodmoon.invasion.raid.lost", f.name).withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer q : level.players()) {
            if (q.distanceToSqr(raid.gate.getX(), raid.gate.getY(), raid.gate.getZ()) < 160 * 160) {
                q.sendSystemMessage(msg);
                q.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 0.5F);
            }
        }
        NEXT.put(raid.player, level.getGameTime() + delay(f, level.random));
    }

    private static void dismiss(ServerLevel level, Raid raid) {
        for (UUID u : raid.troops) {
            Entity e = level.getEntity(u);
            if (e != null) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.8, 0.3, 0.05);
                e.discard();
            }
        }
        raid.troops.clear();
    }

    /** La puerta se derrumba: el velo desaparece y el arco queda roto. */
    private static void crumble(ServerLevel level, Raid raid) {
        for (BlockPos q : raid.gateBlocks) {
            var st = level.getBlockState(q);
            if (st.is(Blocks.PURPLE_STAINED_GLASS) || level.random.nextFloat() < 0.45F) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            else if (!st.isAir()) level.setBlock(q, com.agustin.bloodmoon.registry.ModBlocks.CRACKED_BLACK_ROCK_BRICKS.get().defaultBlockState(), 3);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, raid.gate.getX() + 0.5, raid.gate.getY() + 4, raid.gate.getZ() + 0.5, 60, 2, 3, 2, 0.02);
        level.playSound(null, raid.gate, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1F, 0.6F);
    }
}

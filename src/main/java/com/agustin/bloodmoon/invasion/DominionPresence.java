package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.VoidKnight;
import com.agustin.bloodmoon.entity.VoidSkeleton;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El Dominio con cuerpo: cerca de los jugadores, los rangos (Capitanes y Generales) aparecen en sus puestos y los
 * obeliscos tienen guarnición. Lejos de todos vuelven a ser datos (los rangos guardan su vida). Las entidades no se
 * guardan con el chunk: el Dominio es el dueño de su estado.
 */
public final class DominionPresence {
    public static final String RANK_TAG = "bloodmoon_rank";
    private static final int RANK_SPAWN = 64, RANK_DESPAWN = 112, GARRISON_SPAWN = 48, GARRISON_DESPAWN = 96;
    /** (facción << 16 | uid) -> cuerpo del rango. */
    private static final Map<Long, Entity> BODIES = new HashMap<>();
    /** obelisco -> tropas de guardia. */
    private static final Map<Long, List<UUID>> GARRISONS = new HashMap<>();
    private static final Map<Long, Long> NEXT_TROOP = new HashMap<>();

    private DominionPresence() {}

    public static void clear() {
        BODIES.clear();
        GARRISONS.clear();
        NEXT_TROOP.clear();
    }

    private static double nearestPlayer(ServerLevel level, double x, double z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - x, dz = p.getZ() - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return Math.sqrt(best);
    }

    private static boolean loaded(ServerLevel level, int bx, int bz) {
        return level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) != null;
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) return;
        InvasionData data = InvasionData.get(level);
        if (data.factions.isEmpty()) return;
        long now = level.getGameTime();
        for (Faction f : data.factions) {
            if (!f.active) {
                dismissFaction(f);
                continue;
            }
            tickRanks(level, data, f);
            tickGarrisons(level, data, f, now);
        }
    }

    // ------------------------------------------------------------------ rangos

    private static long bodyKey(Faction f, Faction.RankRecord r) {
        return (long) f.id << 16 | (r.uid & 0xFFFF);
    }

    private static void tickRanks(ServerLevel level, InvasionData data, Faction f) {
        for (Faction.RankRecord r : f.ranks) {
            long key = bodyKey(f, r);
            Entity body = BODIES.get(key);
            if (body != null && (body.isRemoved() || !body.isAlive())) {
                BODIES.remove(key);
                body = null;
            }
            if (!r.alive) continue;
            if (body == null) {
                if (r.seat == Faction.RankRecord.NO_SEAT) continue;
                ChunkPos cp = new ChunkPos(r.seat);
                InvasionData.Cell seatCell = data.cells.get(r.seat);
                int st = seatCell == null ? DominionStructures.NONE : seatCell.structure;
                int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8;
                if (r.rank == InvasionRank.KING) { x = f.center.getX(); z = f.center.getZ() + 14; }   // en la arena, frente al portal
                else if (st == DominionStructures.TOWER) {                       // en la corona de la atalaya
                    int[] o = DominionStructures.offset(cp, f, 2, 2);
                    x += o[0]; z += o[1];
                } else if (st == DominionStructures.FORTRESS) {                  // en el patio, entre la puerta y el torreón
                    int[] o = DominionStructures.offset(cp, f, 0, 11);
                    x += o[0]; z += o[1];
                } else if (r.rank == InvasionRank.CAPTAIN) x += 4;               // en el basamento del obelisco
                if (!loaded(level, x, z) || nearestPlayer(level, x, z) > RANK_SPAWN) continue;
                Entity e = spawnRank(level, f, r, x, z);
                if (e != null) BODIES.put(key, e);
            } else if (nearestPlayer(level, body.getX(), body.getZ()) > RANK_DESPAWN) {
                if (body instanceof Mob m) r.hp = Math.max(0.05F, m.getHealth() / m.getMaxHealth());
                body.discard();
                BODIES.remove(key);
                data.setDirty();
            }
        }
    }

    private static Entity spawnRank(ServerLevel level, Faction f, Faction.RankRecord r, int x, int z) {
        EntityType<? extends Mob> type = switch (r.rank) {
            case CAPTAIN -> ModEntities.VOID_CAPTAIN.get();
            case GENERAL -> ModEntities.VOID_GENERAL.get();
            case KING -> ModEntities.VOID_KING.get();
            default -> null;
        };
        if (type == null) return null;
        Mob mob = type.create(level);
        if (mob == null) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        mob.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360F, 0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)), MobSpawnType.EVENT, null);
        if (mob instanceof VoidSkeleton s) s.bindToDominion();
        if (mob instanceof VoidKnight k) k.bindToDominion();
        if (mob instanceof com.agustin.bloodmoon.entity.VoidGeneral g) g.bindToDominion();
        if (mob instanceof com.agustin.bloodmoon.entity.VoidKing k) k.bindToDominion();
        mob.setCustomName(Component.translatable("bloodmoon.invasion.named." + r.rank.name().toLowerCase(java.util.Locale.ROOT), r.name));
        mob.setCustomNameVisible(true);
        mob.setHealth(mob.getMaxHealth() * Math.max(0.05F, r.hp));
        mob.getPersistentData().putIntArray(RANK_TAG, new int[]{f.id, r.uid});
        level.addFreshEntity(mob);
        return mob;
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getPersistentData().contains(RANK_TAG)) return;
        int[] ids = e.getPersistentData().getIntArray(RANK_TAG);
        if (ids.length < 2) return;
        BODIES.values().remove(e);
        InvasionManager.onRankKilled(level, ids[0], ids[1], event.getSource().getEntity());
    }

    // ------------------------------------------------------------------ guarniciones

    private static void tickGarrisons(ServerLevel level, InvasionData data, Faction f, long now) {
        for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
            InvasionData.Cell c = e.getValue();
            boolean ob = c.obelisk && c.obeliskBuilt;
            boolean struct = c.structure != DominionStructures.NONE && c.structureBuilt;
            if (c.faction != f.id || !ob && !struct) continue;
            long key = e.getKey();
            ChunkPos cp = new ChunkPos(key);
            int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8;
            double near = nearestPlayer(level, x, z);
            List<UUID> troops = GARRISONS.computeIfAbsent(key, k -> new ArrayList<>());
            troops.removeIf(u -> {
                Entity t = level.getEntity(u);
                return t == null || !t.isAlive();
            });
            if (near > GARRISON_DESPAWN) {
                for (UUID u : troops) {
                    Entity t = level.getEntity(u);
                    if (t != null) t.discard();
                }
                troops.clear();
                continue;
            }
            if (near > GARRISON_SPAWN || !loaded(level, x, z)) continue;
            int target = switch (struct ? c.structure : 0) {
                case DominionStructures.NEST -> Math.min(7, 4 + f.phase);
                case DominionStructures.TOWER -> 3;
                case DominionStructures.FORTRESS -> Math.min(8, 5 + f.phase);
                default -> Math.min(5, 2 + f.phase);
            };
            if (troops.size() >= target || now < NEXT_TROOP.getOrDefault(key, 0L)) continue;
            NEXT_TROOP.put(key, now + 60);
            int type = struct ? c.structure : 0;
            boolean archer = type == DominionStructures.TOWER || level.random.nextFloat() < 0.4F;
            VoidSkeleton s = (archer ? ModEntities.VOID_ARCHER.get() : ModEntities.VOID_SENTINEL.get()).create(level);
            if (s == null) continue;
            int sx, sz;
            if (type == DominionStructures.TOWER) {          // en la corona (diagonales libres de la trampilla)
                sx = x + (level.random.nextBoolean() ? 2 : -2);
                sz = z + (level.random.nextBoolean() ? 2 : -2);
            } else if (type == DominionStructures.FORTRESS) { // en las esquinas del patio
                sx = x + (level.random.nextBoolean() ? 11 : -11);
                sz = z + (level.random.nextBoolean() ? 11 : -11);
            } else {
                double a = level.random.nextDouble() * Math.PI * 2;
                double rr = type == DominionStructures.NEST ? 16 : 4;   // nido: afuera del borde de la fosa
                sx = x + (int) Math.round(Math.cos(a) * rr);
                sz = z + (int) Math.round(Math.sin(a) * rr);
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            s.moveTo(sx + 0.5, y, sz + 0.5, level.random.nextFloat() * 360F, 0F);
            s.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(sx, y, sz)), MobSpawnType.EVENT, null);
            s.bindToDominion();
            level.addFreshEntity(s);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, sx + 0.5, y + 1, sz + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
            troops.add(s.getUUID());
        }
    }

    /** Un Dominio vencido pierde a sus guardias y rangos con cuerpo. */
    private static void dismissFaction(Faction f) {
        for (Iterator<Map.Entry<Long, Entity>> it = BODIES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entity> e = it.next();
            if ((e.getKey() >> 16) == f.id) {
                e.getValue().discard();
                it.remove();
            }
        }
    }
}

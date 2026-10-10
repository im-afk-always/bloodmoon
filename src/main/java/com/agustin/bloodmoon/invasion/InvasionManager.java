package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.BloodMoonManager;
import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.MoonType;
import com.agustin.bloodmoon.network.DominionMapPayload;
import com.agustin.bloodmoon.world.ColiseumSites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Invasión del Vacío. Encender el portal del zigurat de un Coliseo despierta un Dominio con eje en ese portal.
 * El Dominio vive en una grilla abstracta de chunks (no carga nada) y avanza cada ciclo:
 * <ol>
 *   <li>gana esencia (goteo + territorio muerto; x3 en la Noche sin Luna);</li>
 *   <li>los obeliscos irradian influencia gratis a su alrededor;</li>
 *   <li>compra avance sobre la frontera: cuesta más lejos del eje {@code 1 + (d/333)²} y el triple sobre el agua;</li>
 *   <li>levanta obeliscos nuevos cerca de la frontera y completa su jerarquía según la fase.</li>
 * </ol>
 * Se vence matando al Observador Desatado tras entrar por ese portal: el Dominio cae, la tierra sana de afuera hacia
 * adentro y el Relicario aparece junto al portal.
 */
public final class InvasionManager {
    public static final String LINK_KEY = "bloodmoon_invasion_link";
    private static final double[] PHASE_AT = {0, 2500, 15000, 60000, 100000};
    /** Nivel de la horda (0-10) según la esencia ganada: decide el equipo de sus soldados. */
    private static final double[] LEVEL_AT = {0, 1000, 2500, 6000, 10000, 15000, 25000, 40000, 60000, 90000, 130000};

    public static int hordeLevel(Faction f) {
        if (f == null) return 0;
        int lv = 0;
        for (int i = 0; i < LEVEL_AT.length; i++) if (f.earned >= LEVEL_AT[i]) lv = i;
        return Math.min(10, lv + (f.soulStage >= 3 ? 1 : 0));
    }
    private static final int[] MAX_GRANTS = {12, 14, 18, 24, 24};
    private static final int OBELISK_COST = 25, OBELISK_AURA = 4;   // la mitad de obeliscos, con más alcance
    private static int cycleTimer;

    private InvasionManager() {}

    public static int radius() {
        return BloodMoonConfig.INVASION_RADIUS.get();
    }

    static int coliseumChunks() {
        return (ColiseumSites.reach() + 16) / 16;
    }

    // ------------------------------------------------------------------ tick y carga de chunks

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        DominionTerraform.tick(level);
        ConstructionSites.tick(level);
        DominionPresence.tick(level);
        InvasionRaids.tick(level);
        FirstSoul.tick(level);
        if (++cycleTimer < BloodMoonConfig.INVASION_CYCLE_SECONDS.get() * 20) return;
        cycleTimer = 0;
        runCycle(level);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        InvasionData data = InvasionData.get(level);
        if (data.cells.isEmpty()) return;
        long key = chunk.getPos().toLong();
        InvasionData.Cell c = data.cells.get(key);
        if (c != null && DominionTerraform.needsWork(data, c)) DominionTerraform.enqueue(key);
        // una estructura mayor espera a que carguen todos sus chunks: avisar a los vecinos
        ChunkPos cp = chunk.getPos();
        for (int ox = -5; ox <= 5; ox++) for (int oz = -5; oz <= 5; oz++) {
            if (ox == 0 && oz == 0) continue;
            long k = ChunkPos.asLong(cp.x + ox, cp.z + oz);
            InvasionData.Cell n = data.cells.get(k);
            if (n == null || n.structure == DominionStructures.NONE || n.structureBuilt) continue;
            int rr = DominionStructures.footprint(n.structure);
            if (Math.abs(ox) <= rr && Math.abs(oz) <= rr && DominionTerraform.needsWork(data, n)) DominionTerraform.enqueue(k);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DominionTerraform.clear();
        ConstructionSites.clear();
        DominionTemplates.clear();
        FirstSoul.clear();
        DominionPresence.clear();
        InvasionRaids.clear();
        cycleTimer = 0;
    }

    // ------------------------------------------------------------------ despertar

    /** Al encender un portal del Vacío: si es el del zigurat de un coliseo, despierta su Dominio. */
    public static void onPortalLit(ServerLevel level, BlockPos portal) {
        if (level.dimension() != Level.OVERWORLD) return;
        Optional<ColiseumSites.Site> site = ColiseumSites.nearest(level, portal, 1);
        if (site.isEmpty()) return;
        BlockPos c = site.get().center();
        double dx = c.getX() - portal.getX(), dz = c.getZ() - portal.getZ();
        if (dx * dx + dz * dz > 24 * 24) return;
        awaken(level, portal);
    }

    /** Dominio cuyo eje está a menos de 32 bloques (o null). */
    static Faction factionAt(InvasionData data, BlockPos pos) {
        for (Faction f : data.factions) {
            if (f.center.distSqr(new BlockPos(pos.getX(), f.center.getY(), pos.getZ())) < 32 * 32) return f;
        }
        return null;
    }

    public static Faction awaken(ServerLevel level, BlockPos portal) {
        InvasionData data = InvasionData.get(level);
        Faction existing = factionAt(data, portal);
        if (existing != null) return existing;   // ya despierto (o purificado: no vuelve)
        RandomSource r = level.random;
        Faction f = new Faction();
        f.id = data.nextId++;
        f.name = DominionNames.faction(r);
        f.center = portal.immutable();
        f.awakenedAt = level.getGameTime();
        f.forgers = 3;
        f.newRank(InvasionRank.CAPTAIN, DominionNames.person(r)).seat = ChunkPos.asLong(portal);
        data.factions.add(f);

        // el coliseo se corrompe entero de golpe: el centro muere y el resto queda marchito
        ChunkPos cc = new ChunkPos(portal);
        int reach = coliseumChunks();
        List<Long> loaded = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > reach * reach) continue;
            long key = ChunkPos.asLong(cc.x + dx, cc.z + dz);
            InvasionData.Cell cell = claim(data, key, f);
            if (cell == null) continue;
            cell.influence = Math.max(cell.influence, d2 <= 64 ? 100 : 60);
            if (level.getChunkSource().getChunkNow(cc.x + dx, cc.z + dz) != null) loaded.add(key);
        }
        loaded.sort(Comparator.comparingLong(k -> d2(new ChunkPos(k), cc)));
        loaded.forEach(DominionTerraform::enqueue);
        data.setDirty();

        Component msg = Component.translatable("bloodmoon.invasion.awaken", f.name).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(msg);
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        level.playSound(null, portal, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3F, 0.4F);
        BloodMoonMod.LOGGER.info("Void Dominion {} awakened at {}", f.name, portal);
        return f;
    }

    /** Toma un chunk para el Dominio si está libre, es suyo o de un Dominio ya vencido. */
    public static InvasionData.Cell claim(InvasionData data, long key, Faction f) {
        InvasionData.Cell c = data.cells.get(key);
        if (c == null) {
            c = new InvasionData.Cell();
            c.faction = f.id;
            data.cells.put(key, c);
            return c;
        }
        if (c.faction == f.id) return c;
        Faction owner = data.faction(c.faction);
        if (owner != null && (owner.active || owner.healing)) return null;
        c.faction = f.id;
        c.obelisk = false;
        return c;
    }

    // ------------------------------------------------------------------ ciclo

    public static void runCycle(ServerLevel level) {
        InvasionData data = InvasionData.get(level);
        if (data.factions.isEmpty()) return;
        Set<Long> changed = new HashSet<>();
        for (Faction f : data.factions) {
            if (f.active) cycleActive(level, data, f, changed);
            else if (f.healing) cycleHealing(level, data, f, changed);
        }
        if (changed.isEmpty()) return;
        data.setDirty();
        for (long key : changed) {
            ChunkPos cp = new ChunkPos(key);
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) != null) DominionTerraform.enqueue(key);
        }
    }

    static long d2(ChunkPos a, ChunkPos b) {
        long dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    static double distance(Faction f, long key) {
        ChunkPos cp = new ChunkPos(key);
        double dx = cp.getMiddleBlockX() - f.center.getX(), dz = cp.getMiddleBlockZ() - f.center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean water(ServerLevel level, Faction f, long key) {
        return f.water.computeIfAbsent(key, k -> {
            ChunkPos cp = new ChunkPos(k);
            var gen = level.getChunkSource().getGenerator();
            Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(cp.getMiddleBlockX()), QuartPos.fromBlock(64),
                    QuartPos.fromBlock(cp.getMiddleBlockZ()), level.getChunkSource().randomState().sampler());
            return b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER);
        });
    }

    /**
     * ¿Hay agua en más de un cuarto del chunk? Con el chunk cargado se mira la superficie real (lagos, pantanos, ríos);
     * si no, el terreno base del generador contra el nivel del mar.
     */
    static boolean wetChunk(ServerLevel level, Faction f, long key) {
        return f.wet.computeIfAbsent(key, k -> {
            if (water(level, f, k)) return true;
            ChunkPos cp = new ChunkPos(k);
            LevelChunk ch = level.getChunkSource().getChunkNow(cp.x, cp.z);
            int wet = 0, n = 0;
            int step = ch != null ? 4 : 8;
            for (int ix = step / 2; ix < 16; ix += step) for (int iz = step / 2; iz < 16; iz += step) {
                n++;
                int x = cp.getMinBlockX() + ix, z = cp.getMinBlockZ() + iz;
                if (ch != null) {
                    int y = ch.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, ix, iz);
                    if (!ch.getFluidState(x, y, z).isEmpty()) wet++;
                } else {
                    int h = level.getChunkSource().getGenerator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG,
                            level, level.getChunkSource().randomState());
                    if (h < level.getSeaLevel() - 1) wet++;
                }
            }
            return wet * 4 > n;
        });
    }

    /** La huella de una estructura: el chunk central seco y como mucho un 10% de chunks con agua. */
    static boolean footprintWet(ServerLevel level, Faction f, ChunkPos cp, int r) {
        if (wetChunk(level, f, cp.toLong())) return true;
        int wet = 0, allowed = (2 * r + 1) * (2 * r + 1) / 10;
        for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
            if (wetChunk(level, f, ChunkPos.asLong(cp.x + ox, cp.z + oz)) && ++wet > allowed) return true;
        }
        return false;
    }

    /** Penalización por distancia al eje (y por agua). */
    static double cost(ServerLevel level, Faction f, long key) {
        double d = distance(f, key) / 333.0;
        return (1 + d * d) * (water(level, f, key) ? 3 : 1);
    }

    private static void cycleActive(ServerLevel level, InvasionData data, Faction f, Set<Long> changed) {
        RandomSource r = level.random;
        double speed = BloodMoonConfig.INVASION_SPEED.get();
        List<Long> dead = new ArrayList<>();
        List<Long> obelisks = new ArrayList<>();
        List<Long> nests = new ArrayList<>(), towers = new ArrayList<>(), fortresses = new ArrayList<>();
        for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
            InvasionData.Cell c = e.getValue();
            if (c.faction != f.id) continue;
            if (c.influence >= 100) dead.add(e.getKey());
            if (c.obelisk) obelisks.add(e.getKey());
            switch (c.structure) {
                case DominionStructures.NEST -> nests.add(e.getKey());
                case DominionStructures.TOWER -> towers.add(e.getKey());
                case DominionStructures.FORTRESS -> fortresses.add(e.getKey());
                default -> { }
            }
        }

        // 1) esencia
        double moon = BloodMoonManager.current() == MoonType.MOONLESS ? 3 : 1;
        double income = (14 + 0.012 * dead.size()) * speed * moon;
        f.earned += income;
        double feed = FirstSoul.feedShare(f, income);   // la Ofrenda se lleva la mitad del ingreso
        f.soulProgress += feed;
        f.essence = Math.min(2000 + 500 * f.phase, f.essence + income - feed);
        int phase = 0;
        for (int i = 0; i < PHASE_AT.length; i++) if (f.earned >= PHASE_AT[i]) phase = i;
        if (phase > f.phase) {
            f.phase = phase;
            announcePhase(level, f);
        }

        int maxR = radius();
        // 2) los obeliscos irradian
        for (long ob : obelisks) {
            ChunkPos o = new ChunkPos(ob);
            for (int dx = -OBELISK_AURA; dx <= OBELISK_AURA; dx++) for (int dz = -OBELISK_AURA; dz <= OBELISK_AURA; dz++) {
                if (dx * dx + dz * dz > OBELISK_AURA * OBELISK_AURA + 1) continue;
                long k = ChunkPos.asLong(o.x + dx, o.z + dz);
                if (distance(f, k) > maxR) continue;
                InvasionData.Cell c = claim(data, k, f);
                if (c == null || c.influence >= 100) continue;
                c.influence = Math.min(100, c.influence + (int) Math.ceil(6 * speed));
                changed.add(k);
            }
        }

        // 3) obeliscos nuevos (antes que la frontera, para que la esencia no se vaya toda en avanzar): 1 cada 100 chunks muertos
        if (dead.size() / 200 > obelisks.size() && f.essence >= OBELISK_COST) {   // 1 cada 200 chunks muertos
            Long best = null;
            double bestD = -1;
            int col = coliseumChunks() + 2;
            for (long k : dead) {
                ChunkPos cp = new ChunkPos(k);
                ChunkPos cc = new ChunkPos(f.center);
                if (d2(cp, cc) <= (long) col * col) continue;
                boolean near = false;
                for (long ob : obelisks) if (d2(new ChunkPos(ob), cp) < 49) { near = true; break; }
                for (long st : anchors(f, List.of(), nests, towers, fortresses)) if (d2(new ChunkPos(st), cp) < 9) { near = true; break; }
                if (f.soulSite != Faction.RankRecord.NO_SEAT && d2(new ChunkPos(f.soulSite), cp) < 50) near = true;
                if (near || wetChunk(level, f, k)) continue;
                double d = distance(f, k) + r.nextDouble() * 48;
                if (d > bestD) { bestD = d; best = k; }
            }
            if (best != null && data.cells.get(best).structure == DominionStructures.NONE) {
                InvasionData.Cell c = data.cells.get(best);
                c.obelisk = true;
                c.obeliskBuilt = false;
                f.essence -= OBELISK_COST;
                obelisks.add(best);
                changed.add(best);
                buildRoad(data, f, best, anchors(f, obelisks, nests, towers, fortresses), changed);
            }
        }

        // 3b) estructuras mayores: nidos y atalayas (Arraigo), fortalezas (Conquista); una por ciclo
        placeStructure(level, data, f, r, dead, obelisks, nests, towers, fortresses, changed);
        // 3c) la Ofrenda a la Primera Alma (fase 4)
        FirstSoul.cycle(level, data, f, dead, anchors(f, obelisks, nests, towers, fortresses), changed);

        // 4) frontera
        Map<Long, Integer> cand = new HashMap<>();
        for (long k : dead) {
            ChunkPos cp = new ChunkPos(k);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long n = ChunkPos.asLong(cp.x + d[0], cp.z + d[1]);
                InvasionData.Cell c = data.cells.get(n);
                if (c != null && c.faction == f.id && c.influence >= 100) continue;
                if (c != null && c.faction != f.id) {
                    Faction owner = data.faction(c.faction);
                    if (owner != null && (owner.active || owner.healing)) continue;
                }
                if (distance(f, n) > maxR) continue;
                cand.merge(n, 1, Integer::sum);
            }
        }
        List<Map.Entry<Long, Double>> scored = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : cand.entrySet()) {
            double s = e.getValue() / cost(level, f, e.getKey()) * (0.6 + 0.8 * r.nextDouble());
            scored.add(Map.entry(e.getKey(), s));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        // faros encendidos: no se reclama tierra a su alcance y la que ya era del Dominio retrocede
        Set<Long> shield = beaconShield(level);
        if (!shield.isEmpty()) {
            scored.removeIf(e -> shield.contains(e.getKey()));
            for (long k : shield) {
                InvasionData.Cell c = data.cells.get(k);
                if (c == null || c.faction != f.id || c.influence <= 0 || c.obelisk || c.structure != DominionStructures.NONE) continue;
                c.influence = Math.max(0, c.influence - 25);
                changed.add(k);
            }
        }
        int grants = level.getGameTime() < f.haltedUntil ? 0
                : (int) Math.ceil(MAX_GRANTS[f.phase] * speed * (level.getGameTime() < f.slowedUntil ? 0.5 : 1));
        for (Map.Entry<Long, Double> e : scored) {
            if (grants <= 0) break;
            double c = cost(level, f, e.getKey());
            if (f.essence < c) break;
            InvasionData.Cell cell = claim(data, e.getKey(), f);
            if (cell == null) continue;
            f.essence -= c;
            cell.influence = Math.min(100, cell.influence + 34);
            changed.add(e.getKey());
            grants--;
        }

        // 5) jerarquía
        List<Long> captainSeats = new ArrayList<>(towers);
        captainSeats.addAll(obelisks);
        updateRanks(level, data, f, r, dead, captainSeats, fortresses);
    }

    private static List<Long> anchors(Faction f, List<Long> obelisks, List<Long> nests, List<Long> towers, List<Long> fortresses) {
        List<Long> a = new ArrayList<>(obelisks);
        a.addAll(nests);
        a.addAll(towers);
        a.addAll(fortresses);
        return a;
    }

    /**
     * Una estructura mayor por ciclo, en orden de prioridad (Fortaleza, Aguja, Atalaya, Nido); si la primera no encuentra
     * lugar se prueba la siguiente (antes, una Fortaleza sin lugar trababa a todas las demás).
     * La separación se mide solo contra otras estructuras mayores; de los obeliscos basta con que no caigan en la huella.
     */
    private static void placeStructure(ServerLevel level, InvasionData data, Faction f, RandomSource r, List<Long> dead, List<Long> obelisks,
                                       List<Long> nests, List<Long> towers, List<Long> fortresses, Set<Long> changed) {
        if (f.phase < 1 || dead.isEmpty()) return;
        List<Long> spires = new ArrayList<>();
        for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
            if (e.getValue().faction == f.id && e.getValue().structure == DominionStructures.SPIRE) spires.add(e.getKey());
        }
        int fortTarget = f.phase >= 2 ? Math.min(InvasionRank.GENERAL.max, 1 + dead.size() / 1500) : 0;
        int spireTarget = f.phase >= 2 ? Math.min(3, 1 + dead.size() / 2000) : 0;
        List<int[]> wanted = new ArrayList<>();   // {tipo, costo}
        if (fortresses.size() < fortTarget && f.essence >= 150) wanted.add(new int[]{DominionStructures.FORTRESS, 150});
        if (spires.size() < spireTarget && f.essence >= 200) wanted.add(new int[]{DominionStructures.SPIRE, 200});
        // mientras falten Fortalezas o Agujas, no se gastan lugares en las menores (la mitad que antes: menos núcleos)
        boolean majorPending = fortresses.size() < fortTarget || spires.size() < spireTarget;
        if (!majorPending || wanted.isEmpty() || r.nextFloat() < 0.25F) {   // si la grande no encuentra lugar, igual avanzan de a poco
            if (dead.size() / 600 > towers.size() && f.essence >= 50) wanted.add(new int[]{DominionStructures.TOWER, 50});
            if (dead.size() / 500 > nests.size() && f.essence >= 40) wanted.add(new int[]{DominionStructures.NEST, 40});
        }
        if (wanted.isEmpty()) return;
        List<Long> majors = new ArrayList<>(fortresses);
        majors.addAll(spires);
        if (f.soulSite != Faction.RankRecord.NO_SEAT) majors.add(f.soulSite);
        List<Long> minors = new ArrayList<>(nests);
        minors.addAll(towers);
        // la banda de distancia se ajusta a lo que el Dominio ya ocupa (al principio no llega al 25% del radio)
        double reach = 0;
        for (long k : dead) reach = Math.max(reach, distance(f, k));
        ChunkPos cc = new ChunkPos(f.center);
        List<Long> order = new ArrayList<>(dead);
        java.util.Collections.shuffle(order, new java.util.Random(r.nextLong()));
        for (int[] w : wanted) {
            int type = w[0];
            boolean major = type == DominionStructures.FORTRESS || type == DominionStructures.SPIRE;
            int fr = DominionStructures.footprint(type);
            double minD = major ? Math.min(radius() * 0.2, reach * 0.3) : type == DominionStructures.NEST ? Math.min(radius() * 0.12, reach * 0.2) : 0;
            boolean far = type == DominionStructures.TOWER;
            int col = coliseumChunks() + 2 + fr;   // la huella no toca el coliseo
            Long best = null;
            double bestScore = -1;
            int checked = 0;
            for (long k : order) {
                if (++checked > 4000) break;
                ChunkPos cp = new ChunkPos(k);
                if (d2(cp, cc) <= (long) col * col) continue;
                if (distance(f, k) < minD) continue;
                if (!footprintFree(data, f, cp, fr)) continue;
                // separación: grandes entre sí 10 chunks; con las menores, solo que las huellas no se toquen
                boolean crowded = false;
                for (long o : majors) {
                    int need = major ? 10 : DominionStructures.footprint(data.cells.get(o).structure) + fr + 2;
                    if (d2(new ChunkPos(o), cp) < (long) need * need) { crowded = true; break; }
                }
                if (!crowded) for (long o : minors) {
                    int need = major ? fr + 3 : 7;
                    if (d2(new ChunkPos(o), cp) < (long) need * need) { crowded = true; break; }
                }
                if (crowded || footprintWet(level, f, cp, fr)) continue;   // nada de estructuras sumergidas
                double score = far ? distance(f, k) + r.nextDouble() * 64 : r.nextDouble();
                if (score > bestScore) { bestScore = score; best = k; }
                if (!far && best != null) break;   // la primera que sirve (el orden ya es al azar)
            }
            if (best == null) continue;   // no hay lugar para esta: probar la siguiente
            InvasionData.Cell c = data.cells.get(best);
            c.structure = type;
            c.structureBuilt = false;
            f.essence -= w[1];
            switch (type) {
                case DominionStructures.NEST -> nests.add(best);
                case DominionStructures.TOWER -> towers.add(best);
                case DominionStructures.FORTRESS -> fortresses.add(best);
                default -> { }
            }
            changed.add(best);
            buildRoad(data, f, best, anchors(f, obelisks, nests, towers, fortresses), changed);
            return;
        }
    }

    /** Una estructura mayor ocupa 3×3 chunks: todos de tierra muerta propia, sin obeliscos ni otras estructuras. */
    private static boolean footprintFree(InvasionData data, Faction f, ChunkPos cp) {
        return footprintFree(data, f, cp, 1);
    }

    static boolean footprintFree(InvasionData data, Faction f, ChunkPos cp, int r) {
        for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
            InvasionData.Cell c = data.cells.get(ChunkPos.asLong(cp.x + ox, cp.z + oz));
            if (c == null || c.faction != f.id || c.influence < 100 || c.obelisk || c.structure != DominionStructures.NONE) return false;
        }
        return true;
    }

    /** Camino de roca negra desde una estructura hasta la más cercana que esté más cerca del eje (o el eje). */
    private static void buildRoad(InvasionData data, Faction f, long from, List<Long> anchors, Set<Long> changed) {
        ChunkPos a = new ChunkPos(from);
        double da = distance(f, from);
        ChunkPos target = new ChunkPos(f.center);
        long best = d2(a, target);
        for (long k : anchors) {
            if (k == from || distance(f, k) >= da - 16) continue;
            long d = d2(new ChunkPos(k), a);
            if (d < best) { best = d; target = new ChunkPos(k); }
        }
        int x = a.x, z = a.z;
        for (int steps = 0; (x != target.x || z != target.z) && steps < 200; steps++) {
            int adx = Math.abs(target.x - x), adz = Math.abs(target.z - z);
            int mx = adx * 2 >= adz ? Integer.signum(target.x - x) : 0;
            int mz = adz * 2 >= adx ? Integer.signum(target.z - z) : 0;
            long k1 = ChunkPos.asLong(x, z), k2 = ChunkPos.asLong(x + mx, z + mz);
            InvasionData.Cell c1 = claim(data, k1, f), c2 = claim(data, k2, f);
            if (c1 == null || c2 == null) break;
            c1.roadMask |= DominionStructures.dirBit(mx, mz);
            c1.roadBuilt = false;
            c2.roadMask |= DominionStructures.dirBit(-mx, -mz);
            c2.roadBuilt = false;
            changed.add(k1);
            changed.add(k2);
            x += mx;
            z += mz;
        }
    }

    /** Un día de juego: lo que tarda en ascender un reemplazo cuando cae un rango. */
    private static final long PROMOTION_DELAY = 24000L;

    private static void updateRanks(ServerLevel level, InvasionData data, Faction f, RandomSource r, List<Long> dead, List<Long> obelisks,
                                    List<Long> fortresses) {
        f.forgers = 3 + f.phase * 3;
        f.troops = dead.size() / 15;
        int captains = Math.min(InvasionRank.CAPTAIN.max, 1 + obelisks.size() / 3);
        int generals = f.phase >= 2 ? Math.min(InvasionRank.GENERAL.max, 1 + dead.size() / 1500) : 0;
        int kings = f.phase >= 3 ? 1 : 0;
        long now = level.getGameTime();
        fill(f, r, InvasionRank.CAPTAIN, captains, now);
        fill(f, r, InvasionRank.GENERAL, generals, now);
        fill(f, r, InvasionRank.KING, kings, now);
        // asientos: Capitanes en obeliscos sin capitán; Generales en tierra muerta a media distancia; el Rey en el eje
        java.util.Set<Long> taken = new HashSet<>();
        for (Faction.RankRecord rr : f.ranks) if (rr.alive && rr.seat != Faction.RankRecord.NO_SEAT) taken.add(rr.seat);
        for (Faction.RankRecord rr : f.ranks) {
            if (!rr.alive || rr.seat != Faction.RankRecord.NO_SEAT) continue;
            if (rr.rank == InvasionRank.KING) rr.seat = ChunkPos.asLong(f.center);
            else if (rr.rank == InvasionRank.CAPTAIN) {
                for (long ob : obelisks) if (!taken.contains(ob)) { rr.seat = ob; break; }
            } else if (rr.rank == InvasionRank.GENERAL && !dead.isEmpty()) {
                for (long fo : fortresses) if (!taken.contains(fo)) { rr.seat = fo; break; }
                for (int tries = 0; tries < 40 && rr.seat == Faction.RankRecord.NO_SEAT; tries++) {
                    long k = dead.get(r.nextInt(dead.size()));
                    double d = distance(f, k);
                    if (d > radius() * 0.35 && d < radius() * 0.8 && !taken.contains(k)) { rr.seat = k; break; }
                }
            }
            if (rr.seat != Faction.RankRecord.NO_SEAT) taken.add(rr.seat);
        }
        // los caídos de hace más de tres días salen de la lista
        f.ranks.removeIf(rr -> !rr.alive && now - rr.diedAt > 3 * PROMOTION_DELAY);
    }

    /** Completa los puestos vacíos, pero un caído se reemplaza recién un día después. */
    private static void fill(Faction f, RandomSource r, InvasionRank rank, int target, long now) {
        long alive = f.aliveCount(rank);
        if (alive >= target) return;
        long lastDeath = 0;
        for (Faction.RankRecord rr : f.ranks) if (rr.rank == rank && !rr.alive) lastDeath = Math.max(lastDeath, rr.diedAt);
        long delay = rank == InvasionRank.KING ? 3 * PROMOTION_DELAY : PROMOTION_DELAY;   // interregno: tres días sin Rey
        if (lastDeath > 0 && now - lastDeath < delay) return;
        for (long i = alive; i < target; i++) f.newRank(rank, DominionNames.person(r));
    }

    /** Un rango del Dominio murió a manos de alguien. */
    public static void onRankKilled(ServerLevel level, int factionId, int uid, net.minecraft.world.entity.Entity killer) {
        InvasionData data = InvasionData.get(level.getServer().overworld());
        Faction f = data.faction(factionId);
        if (f == null) return;
        Faction.RankRecord rr = f.rankByUid(uid);
        if (rr == null || !rr.alive) return;
        rr.alive = false;
        rr.diedAt = level.getGameTime();
        rr.seat = Faction.RankRecord.NO_SEAT;
        if (rr.rank == InvasionRank.KING) {
            f.essence = Math.max(0, f.essence - 2000);
            f.haltedUntil = level.getGameTime() + 3 * PROMOTION_DELAY;
            data.setDirty();
            Component kmsg = Component.translatable("bloodmoon.invasion.king_fell", rr.name, f.name)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(kmsg);
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1F, 0.8F);
            }
            return;
        } else if (rr.rank == InvasionRank.GENERAL) {
            f.essence = Math.max(0, f.essence - 400);
            f.slowedUntil = level.getGameTime() + PROMOTION_DELAY;
        } else if (rr.rank == InvasionRank.CAPTAIN) {
            f.essence = Math.max(0, f.essence - 100);
        }
        data.setDirty();
        Component msg = Component.translatable("bloodmoon.invasion.rank_fell", rr.rank.displayName(), rr.name, f.name)
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(killer == null ? p : killer) < 200 * 200) p.sendSystemMessage(msg);
    }

    private static void announcePhase(ServerLevel level, Faction f) {
        Component msg = Component.translatable("bloodmoon.invasion.phase_up", f.name,
                Component.translatable("bloodmoon.invasion.phase." + f.phase)).withStyle(ChatFormatting.DARK_PURPLE);
        double r2 = Math.pow(radius() + 500, 2);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(f.center) < r2) {
                p.sendSystemMessage(msg);
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1F, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------ núcleos

    public static void onCoreRemoved(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return;
        InvasionData data = InvasionData.get(level);
        long key = ChunkPos.asLong(pos);
        InvasionData.Cell c = data.cells.get(key);
        if (c != null && c.structure == DominionStructures.SOUL) {   // el Corazón del cristal de la Primera Alma
            Faction sf = data.faction(c.faction);
            if (sf != null && sf.active) FirstSoul.onHeartBroken(level, sf);
            data.setDirty();
            return;
        }
        if (c == null || !c.hasCore()) return;
        Faction f = data.faction(c.faction);
        if (f == null || !f.active) return;
        boolean wasStructure = !c.obelisk;
        c.obelisk = false;
        c.structure = DominionStructures.NONE;   // queda en ruinas
        f.essence = Math.max(0, f.essence - (wasStructure ? 120 : 50));
        ChunkPos o = new ChunkPos(key);
        for (int dx = -OBELISK_AURA; dx <= OBELISK_AURA; dx++) for (int dz = -OBELISK_AURA; dz <= OBELISK_AURA; dz++) {
            InvasionData.Cell n = data.cells.get(ChunkPos.asLong(o.x + dx, o.z + dz));
            if (n != null && n.faction == f.id) n.influence = Math.max(0, n.influence - 60);
        }
        data.setDirty();
        Component msg = Component.translatable("bloodmoon.invasion.obelisk_fell", f.name).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(pos) < 160 * 160) {
                p.sendSystemMessage(msg);
                p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------ final: el Observador Desatado cae

    /** El jugador cruza un portal del Overworld: si es el eje de un Dominio, queda vinculado a él. */
    public static void linkPlayer(ServerPlayer player, BlockPos portal) {
        InvasionData data = InvasionData.get(player.server.overworld());
        Faction f = factionAt(data, portal);
        if (f != null && f.active) player.getPersistentData().putInt(LINK_KEY, f.id);
    }

    public static void onUnboundDefeated(MinecraftServer server, List<ServerPlayer> participants) {
        ServerLevel overworld = server.overworld();
        InvasionData data = InvasionData.get(overworld);
        Set<Integer> ids = new HashSet<>();
        for (ServerPlayer p : participants) {
            if (p.getPersistentData().contains(LINK_KEY)) ids.add(p.getPersistentData().getInt(LINK_KEY));
            p.getPersistentData().remove(LINK_KEY);
        }
        if (ids.isEmpty()) for (Faction f : data.factions) if (f.active) ids.add(f.id);   // sin vínculo: caen todos
        for (int id : ids) {
            Faction f = data.faction(id);
            if (f != null && f.active) defeat(overworld, data, f);
        }
    }

    public static void defeat(ServerLevel level, InvasionData data, Faction f) {
        f.active = false;
        f.healing = true;
        for (InvasionData.Cell c : data.cells.values()) if (c.faction == f.id) { c.obelisk = false; c.structure = DominionStructures.NONE; }
        for (Faction.RankRecord r : f.ranks) r.alive = false;
        dropRelic(level, f);
        data.setDirty();
        Component msg = Component.translatable("bloodmoon.invasion.defeated", f.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1F, 0.7F);
        }
        // los chunks cargados con núcleos los pierden ya
        for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
            if (e.getValue().faction != f.id) continue;
            ChunkPos cp = new ChunkPos(e.getKey());
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) != null && DominionTerraform.needsWork(data, e.getValue())) {
                DominionTerraform.enqueue(e.getKey());
            }
        }
    }

    /** El Relicario: cofres junto al portal con todo lo que el Dominio se tragó. */
    private static void dropRelic(ServerLevel level, Faction f) {
        if (f.relic.isEmpty()) return;
        List<ItemStack> items = new ArrayList<>(f.relic);
        f.relic.clear();
        level.getChunk(f.center);
        int placed = 0;
        for (int i = 0; i < 16 && !items.isEmpty(); i++) {
            int dx = (i % 4) - 1, dz = 2 + i / 4;
            BlockPos p = f.center.offset(dx, 0, (i % 2 == 0 ? 1 : -1) * dz);
            if (!level.getBlockState(p).isAir()) continue;
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                for (int s = 0; s < chest.getContainerSize() && !items.isEmpty(); s++) chest.setItem(s, items.remove(0));
                placed++;
            }
        }
        for (ItemStack st : items) {   // si no entró todo, cae al piso junto al portal
            net.minecraft.world.entity.item.ItemEntity ie = new net.minecraft.world.entity.item.ItemEntity(level,
                    f.center.getX() + 0.5, f.center.getY() + 1, f.center.getZ() + 0.5, st);
            level.addFreshEntity(ie);
        }
        BloodMoonMod.LOGGER.info("Dominion {} relic: {} chests", f.name, placed);
    }

    /** La tierra sana de afuera hacia adentro. */
    private static void cycleHealing(ServerLevel level, InvasionData data, Faction f, Set<Long> changed) {
        List<Long> mine = new ArrayList<>();
        for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
            if (e.getValue().faction == f.id && e.getValue().influence > 0) mine.add(e.getKey());
        }
        if (mine.isEmpty()) {
            f.healing = false;
            return;
        }
        mine.sort((a, b) -> Double.compare(distance(f, b), distance(f, a)));
        int n = Math.min(mine.size(), 80);
        for (int i = 0; i < n; i++) {
            InvasionData.Cell c = data.cells.get(mine.get(i));
            c.influence = Math.max(0, c.influence - 34);
            changed.add(mine.get(i));
        }
    }

    // ------------------------------------------------------------------ mapa

    public static void sendMap(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        InvasionData data = InvasionData.get(overworld);
        List<DominionMapPayload.FactionView> views = new ArrayList<>();
        int maxR = radius();
        int half = maxR / 16 + 1;
        int side = half * 2 + 1;
        for (Faction f : data.factions) {
            if (!f.active && !f.healing) continue;
            if (player.blockPosition().distSqr(new BlockPos(f.center.getX(), player.getBlockY(), f.center.getZ())) > Math.pow(maxR + 4000, 2)) continue;
            byte[] grid = new byte[side * side], structs = new byte[side * side], roads = new byte[side * side];
            ChunkPos cc = new ChunkPos(f.center);
            int dead = 0, obelisks = 0;
            for (Map.Entry<Long, InvasionData.Cell> e : data.cells.entrySet()) {
                InvasionData.Cell c = e.getValue();
                if (c.faction != f.id || c.influence <= 0) continue;
                ChunkPos cp = new ChunkPos(e.getKey());
                int gx = cp.x - cc.x + half, gz = cp.z - cc.z + half;
                if (gx < 0 || gz < 0 || gx >= side || gz >= side) continue;
                int v = c.influence >= 100 && c.obelisk ? 101 : c.influence;
                grid[gz * side + gx] = (byte) v;
                if (c.influence >= 100) {
                    structs[gz * side + gx] = (byte) c.structure;
                    roads[gz * side + gx] = (byte) c.roadMask;
                }
                if (c.influence >= 100) dead++;
                if (c.obelisk) obelisks++;
            }
            List<DominionMapPayload.RankView> ranks = new ArrayList<>();
            for (Faction.RankRecord rr : f.ranks) {
                boolean seated = rr.alive && rr.seat != Faction.RankRecord.NO_SEAT;
                ChunkPos sp = seated ? new ChunkPos(rr.seat) : null;
                ranks.add(new DominionMapPayload.RankView(rr.rank.ordinal(), rr.name, rr.alive,
                        seated ? sp.getMiddleBlockX() : Integer.MIN_VALUE, seated ? sp.getMiddleBlockZ() : Integer.MIN_VALUE));
            }
            views.add(new DominionMapPayload.FactionView(f.id, f.name, f.center.getX(), f.center.getZ(), maxR, f.phase,
                    (int) f.essence, f.active, f.healing, f.forgers, f.troops, dead, obelisks, ranks, half, grid, structs, roads,
                    f.soulStage, (int) Math.min(100, 100 * f.soulProgress / FirstSoul.COST)));
        }
        long[] gates = InvasionRaids.gates().stream().mapToLong(BlockPos::asLong).toArray();
        PacketDistributor.sendToPlayer(player, new DominionMapPayload(views, gates));
    }

    // ------------------------------------------------------------------ comandos

    /**
     * El Trono: el portal del coliseo de un Dominio con Rey vivo está sellado. Avisa al jugador (como mucho cada 3 s).
     */
    public static boolean throneSeals(ServerLevel level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        Faction f = nearest(level, pos);
        if (f == null || !f.active || f.center.distSqr(pos) > 80 * 80) return false;
        Faction.RankRecord king = null;
        for (Faction.RankRecord r : f.ranks) if (r.rank == InvasionRank.KING && r.alive) king = r;
        if (king == null) return false;
        if (entity instanceof ServerPlayer sp) {
            long now = level.getGameTime();
            long last = sp.getPersistentData().getLong("bloodmoon_throne_msg");
            if (now - last > 60) {
                sp.getPersistentData().putLong("bloodmoon_throne_msg", now);
                sp.displayClientMessage(Component.translatable("bloodmoon.invasion.throne_sealed", king.name).withStyle(ChatFormatting.GOLD), true);
                sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1F, 0.6F);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ faros

    /** Faros encendidos cerca de los jugadores: centro y radio de efecto (10 por nivel de pirámide + 10). */
    private static List<int[]> activeBeacons(ServerLevel level) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            ChunkPos pc = p.chunkPosition();
            for (int ox = -6; ox <= 6; ox++) for (int oz = -6; oz <= 6; oz++) {
                long k = ChunkPos.asLong(pc.x + ox, pc.z + oz);
                if (!seen.add(k)) continue;
                net.minecraft.world.level.chunk.LevelChunk ch = level.getChunkSource().getChunkNow(pc.x + ox, pc.z + oz);
                if (ch == null) continue;
                for (net.minecraft.world.level.block.entity.BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity b) || b.getBeamSections().isEmpty()) continue;
                    int lv = pyramid(level, be.getBlockPos());
                    if (lv > 0) out.add(new int[]{be.getBlockPos().getX(), be.getBlockPos().getZ(), lv * 10 + 10});
                }
            }
        }
        return out;
    }

    private static int pyramid(ServerLevel level, BlockPos pos) {
        int lv = 0;
        for (int i = 1; i <= 4; i++) {
            int y = pos.getY() - i;
            for (int x = pos.getX() - i; x <= pos.getX() + i; x++) for (int z = pos.getZ() - i; z <= pos.getZ() + i; z++) {
                if (!level.getBlockState(new BlockPos(x, y, z)).is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS)) return lv;
            }
            lv = i;
        }
        return lv;
    }

    /** Chunks bajo la protección de un faro (su centro dentro del radio de efecto). */
    private static Set<Long> beaconShield(ServerLevel level) {
        Set<Long> out = new HashSet<>();
        for (int[] b : activeBeacons(level)) {
            int r = b[2], c0x = (b[0] - r) >> 4, c1x = (b[0] + r) >> 4, c0z = (b[1] - r) >> 4, c1z = (b[1] + r) >> 4;
            for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) {
                double dx = (cx << 4) + 8 - b[0], dz = (cz << 4) + 8 - b[1];
                if (dx * dx + dz * dz <= (double) r * r) out.add(ChunkPos.asLong(cx, cz));
            }
        }
        return out;
    }

    public static Faction nearest(ServerLevel level, BlockPos pos) {
        Faction best = null;
        double bd = Double.MAX_VALUE;
        for (Faction f : InvasionData.get(level).factions) {
            if (!f.active && !f.healing) continue;
            double d = f.center.distSqr(pos);
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    public static String status(ServerLevel level) {
        InvasionData data = InvasionData.get(level);
        StringBuilder sb = new StringBuilder();
        for (Faction f : data.factions) {
            int dead = 0, all = 0, ob = 0;
            int[] st = new int[8], built = new int[8];
            for (InvasionData.Cell c : data.cells.values()) {
                if (c.faction != f.id || c.influence <= 0) continue;
                all++;
                if (c.influence >= 100) dead++;
                if (c.obelisk) ob++;
                st[c.structure & 7]++;
                if (c.structureBuilt) built[c.structure & 7]++;
            }
            sb.append(String.format("%s [%s] fase %d · nivel %d · esencia %.0f · chunks %d (muertos %d) · obeliscos %d · eje %s%n",
                    f.name, f.active ? "activo" : f.healing ? "sanando" : "vencido", f.phase, hordeLevel(f), f.essence, all, dead, ob,
                    f.center.toShortString()));
            sb.append(String.format("  nidos %d/%d · atalayas %d/%d · fortalezas %d/%d · agujas %d/%d · santuario %s (construidas/totales)%n",
                    built[DominionStructures.NEST], st[DominionStructures.NEST], built[DominionStructures.TOWER], st[DominionStructures.TOWER],
                    built[DominionStructures.FORTRESS], st[DominionStructures.FORTRESS], built[DominionStructures.SPIRE], st[DominionStructures.SPIRE],
                    f.soulStage == 0 ? "-" : f.soulStage + " (" + (int) (100 * f.soulProgress / FirstSoul.COST) + "%)"));
        }
        sb.append("Conversiones pendientes: ").append(DominionTerraform.pending());
        return sb.toString();
    }
}

package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simulación de los asentamientos humanos (Fase 2). Cada ciclo (1 minuto, 1/20 de día) cada asentamiento produce y
 * consume comida, cobra impuestos, crece si hay comida y lugar, decide su próxima obra según lo que le falta y la
 * paga del tesoro. La obra avanza como datos; si sus chunks están cargados se ve subir bloque a bloque con un obrero,
 * y si no, se coloca entera cuando alguien se acerca. Un pueblo grande y próspero manda colonos a fundar otra aldea.
 * Modelo calibrado con una simulación offline y verificado en la prueba de humo (30 días: 22 → ~48 habitantes, sin hambre ni deuda).
 */
public final class HumanityManager {
    public static final int CYCLE = 1200, CYCLES_PER_DAY = 20;
    /** Humanos materializados por asentamiento como máximo (el resto de la población es abstracta). */
    public static final int MATERIAL_CAP = 40;
    public static final int TOWN_POP = 40, COLONY_POP = 60;

    private static final ConcurrentLinkedQueue<Long> REGISTER = new ConcurrentLinkedQueue<>();
    private static int timer;

    private HumanityManager() {}

    // ------------------------------------------------------------------ datos

    public static final class Data extends SavedData {
        private static final String NAME = "bloodmoon_humanity";
        public final Map<Long, Settlement> settlements = new LinkedHashMap<>();

        public static Data get(ServerLevel level) {
            ServerLevel ow = level.getServer().overworld();
            return ow.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (Tag t : tag.getList("settlements", Tag.TAG_COMPOUND)) {
                Settlement s = Settlement.load((CompoundTag) t);
                d.settlements.put(s.key, s);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag l = new ListTag();
            for (Settlement s : settlements.values()) l.add(s.save());
            tag.put("settlements", l);
            return tag;
        }
    }

    public static void clear() {
        REGISTER.clear();
        timer = 0;
    }

    // ------------------------------------------------------------------ eventos

    /** Al cargar el chunk del centro de una aldea del mundo, se la registra (también en mundos viejos). */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        ChunkPos cp = chunk.getPos();
        int rx = Math.floorDiv(cp.x, VillageSites.REGION), rz = Math.floorDiv(cp.z, VillageSites.REGION);
        Optional<VillageSites.Site> s = VillageSites.site(level, rx, rz);
        if (s.isEmpty()) return;
        if ((s.get().x() >> 4) != cp.x || (s.get().z() >> 4) != cp.z) return;
        REGISTER.add(ChunkPos.asLong(rx, rz));
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Human h) || !(h.level() instanceof ServerLevel level)) return;
        if (h.settlement() == 0 || h.isBuilder()) return;
        Data d = Data.get(level);
        Settlement s = d.settlements.get(h.settlement());
        if (s == null) return;
        s.pop = Math.max(0, s.pop - 1);
        s.materialized = Math.max(0, s.materialized - 1);
        d.setDirty();
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Data data = Data.get(level);
        Long r;
        while ((r = REGISTER.poll()) != null) {
            Optional<VillageSites.Site> s = VillageSites.site(level, ChunkPos.getX(r), ChunkPos.getZ(r));
            if (s.isPresent()) register(level, data, s.get());
        }
        if (data.settlements.isEmpty()) return;
        if (++timer >= CYCLE) {
            timer = 0;
            for (Settlement s : new ArrayList<>(data.settlements.values())) cycle(level, data, s);
            data.setDirty();
        }
        int budget = 400;
        for (Settlement s : data.settlements.values()) {
            for (Settlement.Work w : new ArrayList<>(s.works)) {
                if (w.state < 2 && budget > 0) budget -= build(level, data, s, w, budget);
            }
        }
    }

    // ------------------------------------------------------------------ registro

    public static Settlement register(ServerLevel level, Data data, VillageSites.Site site) {
        long key = VillageBuilder.key(site.x(), site.z());
        Settlement s = data.settlements.get(key);
        if (s != null) return s;
        VillageLayout.Layout lay = VillageLayout.get(level, site);
        s = new Settlement();
        s.key = key;
        s.x = site.x();
        s.y = site.y();
        s.z = site.z();
        s.seed = site.seed();
        s.culture = site.culture();
        s.name = townName(site.seed(), site.culture());
        for (VillageLayout.Building b : lay.buildings()) s.pop += b.residents();
        s.materialized = s.pop;
        s.food = s.pop * 3.0;
        s.treasury = 300;
        s.paved = lay.pavedInit().clone();
        s.foundedDay = (int) (level.getDayTime() / 24000L);
        data.settlements.put(key, s);
        data.setDirty();
        BloodMoonMod.LOGGER.info("Humanity: registered {} ({}) at {} {} pop {}", s.name, s.culture, s.x, s.z, s.pop);
        return s;
    }

    // ------------------------------------------------------------------ economía

    static int capacity(VillageLayout.Building b) {
        return switch (b.kind()) {
            case HOUSE -> b.template().contains("large") ? 5 : 3;
            case WORK -> 1;
            case TOWER -> 2;
            default -> 0;
        };
    }

    /** Edificios que cuentan (en pie o terminados como datos). */
    public static List<VillageLayout.Building> built(ServerLevel level, Settlement s) {
        List<VillageLayout.Building> out = new ArrayList<>();
        if (!s.colony) out.addAll(VillageLayout.get(level, s.site()).buildings());
        for (Settlement.Work w : s.works) if (w.state >= 1) out.add(w.b);
        return out;
    }

    public static int housing(ServerLevel level, Settlement s) {
        int h = 0;
        for (VillageLayout.Building b : built(level, s)) h += capacity(b);
        return h;
    }

    private static int count(List<VillageLayout.Building> bs, VillageLayout.Kind k) {
        int n = 0;
        for (VillageLayout.Building b : bs) if (b.kind() == k) n++;
        return n;
    }

    public static double production(List<VillageLayout.Building> bs, int pop) {
        int fishers = 0;
        for (VillageLayout.Building b : bs) if (b.job() == HumanJob.FISHERMAN || b.job() == HumanJob.BUTCHER) fishers++;
        return count(bs, VillageLayout.Kind.FARM) * 7.0 + fishers * 3.0 + 2.0 + pop * 0.3;
    }

    static int cost(VillageLayout.Building b) {
        return switch (b.kind()) {
            case HOUSE -> b.template().contains("large") ? 260 : 150;
            case WORK -> 220;
            case TOWER -> 400;
            case STALL -> 80;
            case FARM -> 100;
            case WELL -> 50;
        };
    }

    static double days(VillageLayout.Building b) {
        return switch (b.kind()) {
            case HOUSE -> b.template().contains("large") ? 1.6 : 1.0;
            case WORK -> 1.2;
            case TOWER -> 2.0;
            case STALL -> 0.5;
            case FARM -> 0.6;
            case WELL -> 0.4;
        };
    }

    public static void cycle(ServerLevel level, Data data, Settlement s) {
        List<VillageLayout.Building> bs = built(level, s);
        int housing = 0;
        for (VillageLayout.Building b : bs) housing += capacity(b);
        double prod = production(bs, s.pop), cons = s.pop;
        s.food += (prod - cons) / CYCLES_PER_DAY;
        if (s.food < 0) {
            // hambre: algunos se van
            if (level.random.nextInt(4) == 0 && s.pop > 4) s.pop--;
            s.food = 0;
        }
        s.food = Math.min(s.food, Math.max(20, s.pop * 20.0));
        // nacimientos
        if (s.food > s.pop * 2.0 && s.pop < housing) {
            s.growth += s.pop * 0.03 / CYCLES_PER_DAY;
            while (s.growth >= 1 && s.pop < housing) {
                s.growth -= 1;
                s.pop++;
                birth(level, s, bs);
            }
        }
        // tesoro
        s.treasury += (s.pop * 2.5 + count(bs, VillageLayout.Kind.STALL) * 8 - count(bs, VillageLayout.Kind.TOWER) * 6) / CYCLES_PER_DAY;
        if (s.treasury < 0) s.treasury = 0;
        // nivel
        int oldLevel = s.level;
        if (s.level == Settlement.VILLAGE && s.pop >= TOWN_POP) s.level = Settlement.TOWN;
        else if (s.level == Settlement.TOWN && s.pop < TOWN_POP - 10) s.level = Settlement.VILLAGE;
        if (s.level != oldLevel) {
            announce(level, s, 320, Component.translatable(s.level == Settlement.TOWN ? "message.bloodmoon.humanity.town" : "message.bloodmoon.humanity.village", s.name));
        }
        // obra
        Settlement.Work cur = s.current();
        if (cur == null) {
            planNext(level, s, bs, housing, prod, cons);
            cur = s.current();
        }
        if (cur != null) {
            boolean mason = false;
            for (VillageLayout.Building b : bs) if (b.job() == HumanJob.MASON) mason = true;
            s.progress += (1.0 / days(cur.b)) * (mason ? 1.3 : 1.0) / CYCLES_PER_DAY;
            if (s.progress >= 1) {
                s.progress = 0;
                cur.state = 1;
                s.pop += cur.b.residents();   // llegan los trabajadores del edificio nuevo
            }
        }
        // colonias
        int day = (int) (level.getDayTime() / 24000L);
        if (s.level >= Settlement.TOWN && s.pop >= COLONY_POP && s.treasury >= 800 && s.food > s.pop * 4.0 && day - s.lastColonyDay >= 5) {
            s.lastColonyDay = day;
            foundColony(level, data, s);
        }
    }

    /** Elige la próxima obra según lo que falta y la paga; si no alcanza el tesoro, espera. */
    private static void planNext(ServerLevel level, Settlement s, List<VillageLayout.Building> bs, int housing, double prod, double cons) {
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        String c = s.culture == Culture.DESERT ? "desert/" : "plains/";
        Random rng = new Random(s.seed ^ (s.works.size() * 0x9E3779B97F4A7C15L));
        String template;
        VillageLayout.Kind kind;
        HumanJob job = HumanJob.NONE;
        int residents = 0;
        if (s.colony && s.colonyNext < lay.buildings().size()) {
            // una colonia levanta primero su plano original, en orden (pozo, mercado, talleres, casas...)
            VillageLayout.Building b = lay.buildings().get(s.colonyNext);
            if (s.treasury < cost(b)) return;
            s.treasury -= cost(b);
            s.colonyNext++;
            Settlement.Work w = new Settlement.Work();
            w.b = b;
            w.arm = lay.buildingSeg()[s.colonyNext - 1];
            w.spur = lay.spurs().get(s.colonyNext - 1);
            s.works.add(w);
            return;
        }
        int pals = s.culture == Culture.DESERT ? 3 : 4;
        if (prod < cons * 1.15) {
            template = c + "farm_" + rng.nextInt(2);
            kind = VillageLayout.Kind.FARM;
        } else if (s.pop >= housing - 1) {
            boolean large = s.pop > 30 && rng.nextBoolean();
            template = c + (large ? "house_large_" : "house_small_") + rng.nextInt(pals);
            kind = VillageLayout.Kind.HOUSE;
        } else if (s.level >= Settlement.TOWN && count(bs, VillageLayout.Kind.TOWER) < 2) {
            template = c + "tower";
            kind = VillageLayout.Kind.TOWER;
            job = HumanJob.GUARD;
            residents = 2;
        } else if (count(bs, VillageLayout.Kind.STALL) < 2 + s.pop / 25) {
            template = c + "stall_" + rng.nextInt(2);
            kind = VillageLayout.Kind.STALL;
            job = HumanJob.MERCHANT;
            residents = 1;
        } else {
            List<HumanJob> missing = new ArrayList<>();
            for (HumanJob j : HumanJob.values()) {
                if (j.vanilla == null) continue;
                boolean has = false;
                for (VillageLayout.Building b : bs) if (b.kind() == VillageLayout.Kind.WORK && b.job() == j) has = true;
                if (!has) missing.add(j);
            }
            if (missing.isEmpty() || count(bs, VillageLayout.Kind.WORK) * 4 > s.pop) return;
            job = missing.get(rng.nextInt(missing.size()));
            template = c + "work_" + job.vanilla;
            kind = VillageLayout.Kind.WORK;
            residents = 1;
        }
        // presupuesto
        int price = switch (kind) {
            case HOUSE -> template.contains("large") ? 260 : 150;
            case WORK -> 220;
            case TOWER -> 400;
            case STALL -> 80;
            default -> 100;
        };
        if (s.treasury < price) return;
        if (s.plotWait > 0) {
            s.plotWait--;
            return;
        }
        // lote
        List<VillageLayout.Building> others = new ArrayList<>(lay.buildings());
        for (Settlement.Work w : s.works) others.add(w.b);
        others.addAll(s.blocked);
        List<VillageLayout.Road> roads = new ArrayList<>(lay.net());
        roads.add(lay.plaza());
        VillageLayout.Plot pl = VillageLayout.findPlot(level, s.site(), lay.net(), lay.dist(), VillageLayout.MAX_DIST, template, kind, job,
                residents, others, roads, b -> !loaded(level, b) || VillageBuilder.artificial(level, b, 3) <= 3, true);
        if (pl == null) {
            s.plotWait = CYCLES_PER_DAY;   // no hay lugar: se vuelve a mirar mañana
            return;
        }
        s.treasury -= price;
        Settlement.Work w = new Settlement.Work();
        w.b = pl.building();
        w.spur = pl.spur();
        w.arm = pl.seg();
        w.d = pl.t();
        s.works.add(w);
    }

    // ------------------------------------------------------------------ obra a la vista

    static boolean loadedBox(ServerLevel level, int[] box) {
        for (int cx = box[0] >> 4; cx <= box[2] >> 4; cx++) {
            for (int cz = box[1] >> 4; cz <= box[3] >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    static boolean loaded(ServerLevel level, VillageLayout.Building b) {
        for (int cx = (b.minX() - 1) >> 4; cx <= (b.maxX() + 1) >> 4; cx++) {
            for (int cz = (b.minZ() - 1) >> 4; cz <= (b.maxZ() + 1) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    /** Coloca lo que corresponde al avance de la obra. Devuelve los bloques usados del presupuesto. */
    private static int build(ServerLevel level, Data data, Settlement s, Settlement.Work w, int budget) {
        if (!loaded(level, w.b)) return 0;
        boolean desert = s.culture == Culture.DESERT;
        int[] box = {w.b.minX() - 1, w.b.minZ() - 1, w.b.maxX() + 1, w.b.maxZ() + 1};
        if (!w.prepared) {
            if (VillageBuilder.artificial(level, w.b, 3) > 3) {
                // el jugador construyó en el lote: se descarta y se devuelve el dinero
                s.works.remove(w);
                s.blocked.add(w.b);
                if (w.state == 0) s.progress = 0;
                s.treasury += cost(w.b);
                data.setDirty();
                return 1;
            }
            VillageLayout.Layout lay = VillageLayout.get(level, s.site());
            int[] ybox = {w.b.minX() - 3, w.b.minZ() - 3, w.b.maxX() + 3, w.b.maxZ() + 3};
            VillageBuilder.clearTrees(level, ybox, List.of(w.b), List.of(), false);
            List<VillageLayout.Road> near = new ArrayList<>();
            near.add(lay.plaza());
            for (int k = 0; k < lay.net().size(); k++) if (k < s.paved.length && s.paved[k]) near.add(lay.net().get(k));
            VillageBuilder.yard(level, w.b, ybox, near, desert, false);
            VillageBuilder.prepare(level, level, w.b, box, desert);
            w.prepared = true;
            if (w.state == 0 && level.getNearestPlayer(w.b.coreX(), w.b.floorY(), w.b.coreZ(), 96, false) != null) spawnBuilder(level, s, w);
            data.setDirty();
            return 40;
        }
        int size = VillageBuilder.size(level, w.b);
        int target = w.state >= 1 ? size : (int) Math.floor(s.progress * size);
        if (w.placed < target) {
            int to = Math.min(target, w.placed + Math.min(budget, w.state >= 1 ? 400 : 6));
            VillageBuilder.place(level, level, w.b, box, w.placed, to);
            if (w.state == 0) dust(level, s, w, to - 1);
            int used = to - w.placed;
            w.placed = to;
            data.setDirty();
            return Math.max(1, used);
        }
        if (w.state >= 1 && w.placed >= size) finish(level, data, s, w);
        return 1;
    }

    private static void dust(ServerLevel level, Settlement s, Settlement.Work w, int index) {
        var vt = VillageLayout.template(level, w.b.template());
        if (index < 0 || index >= vt.t().blocks().size()) return;
        var e = vt.t().blocks().get(index);
        if (e.state().isAir()) return;
        int[] r = com.agustin.bloodmoon.invasion.DominionTemplates.rotate(e.x(), e.z(), w.b.rot());
        BlockPos p = new BlockPos(w.b.x() + r[0], w.b.floorY() + e.y(), w.b.z() + r[1]);
        level.levelEvent(2001, p, Block.getId(e.state()));
    }

    private static void spawnBuilder(ServerLevel level, Settlement s, Settlement.Work w) {
        Human h = ModEntities.HUMAN.get().create(level);
        if (h == null) return;
        BlockPos core = new BlockPos(w.b.coreX(), w.b.floorY() + 1, w.b.coreZ());
        h.moveTo(core.getX() + 0.5, core.getY(), core.getZ() + 0.5, 0, 0);
        h.setup(level.random.nextInt(), HumanJob.MASON, s.culture);
        h.setBuilder(true);
        h.setSettlement(s.key);
        h.setHome(core);
        h.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL));
        h.setPersistenceRequired();
        level.addFreshEntity(h);
    }

    private static void finish(ServerLevel level, Data data, Settlement s, Settlement.Work w) {
        w.state = 2;
        boolean desert = s.culture == Culture.DESERT;
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        List<VillageLayout.Road> roads = new ArrayList<>();
        if (w.spur != null) roads.add(w.spur);
        // abrir la calle hasta el edificio: su tramo y los que lo unen a la plaza
        if (s.paved.length != lay.net().size()) s.paved = java.util.Arrays.copyOf(s.paved, lay.net().size());
        boolean[] fresh = new boolean[lay.net().size()];
        for (int k = w.arm; k >= 0 && k < fresh.length && !s.paved[k]; k = lay.parent()[k]) {
            s.paved[k] = true;
            fresh[k] = true;
            roads.add(lay.net().get(k));
        }
        // una colonia pavimenta su plaza con el pozo
        if (w.b.kind() == VillageLayout.Kind.WELL) roads.add(lay.plaza());
        List<VillageLayout.Building> all = new ArrayList<>(built(level, s));
        for (VillageLayout.Road r : roads) {
            int[] box = {(int) Math.floor(Math.min(r.x0(), r.x1()) - r.half() - 3), (int) Math.floor(Math.min(r.z0(), r.z1()) - r.half() - 3),
                    (int) Math.ceil(Math.max(r.x0(), r.x1()) + r.half() + 3), (int) Math.ceil(Math.max(r.z0(), r.z1()) + r.half() + 3)};
            VillageBuilder.clearTrees(level, box, List.of(), List.of(r), false);
            VillageBuilder.pave(level, box, List.of(r), all, desert, false);
        }
        for (int[] l : VillageLayout.lamps(lay.net(), fresh, all, s.works.size())) {
            if (level.hasChunk(l[0] >> 4, l[1] >> 4)) VillageBuilder.lamp(level, l[0], l[1], desert, false);
        }
        // se va el obrero, llegan los que viven o trabajan ahí
        for (Human h : level.getEntitiesOfClass(Human.class, new net.minecraft.world.phys.AABB(
                w.b.minX() - 24, w.b.floorY() - 8, w.b.minZ() - 24, w.b.maxX() + 24, w.b.floorY() + 16, w.b.maxZ() + 24),
                h -> h.isBuilder() && h.settlement() == s.key)) {
            h.discard();
        }
        int room = Math.max(0, MATERIAL_CAP - s.materialized);
        int n = w.b.kind() == VillageLayout.Kind.WELL && s.colony ? Math.min(room, Math.max(0, s.pop - s.materialized)) : Math.min(room, w.b.residents());
        if (n > 0) s.materialized += VillageBuilder.spawnResidents(level, level, w.b, s.culture, s.seed, s.key, n, false);
        level.playSound(null, w.b.coreX(), w.b.floorY() + 1, w.b.coreZ(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.NEUTRAL, 1.0F, 1.0F);
        data.setDirty();
    }

    /** Un nacimiento: un bebé en la puerta de alguna casa (si hay alguien cerca para verlo y lugar en el tope). */
    private static void birth(ServerLevel level, Settlement s, List<VillageLayout.Building> bs) {
        if (s.materialized >= MATERIAL_CAP) return;
        List<VillageLayout.Building> houses = new ArrayList<>();
        for (VillageLayout.Building b : bs) if (b.kind() == VillageLayout.Kind.HOUSE && level.hasChunk(b.coreX() >> 4, b.coreZ() >> 4)) houses.add(b);
        if (houses.isEmpty()) return;
        VillageLayout.Building b = houses.get(level.random.nextInt(houses.size()));
        Human h = ModEntities.HUMAN.get().create(level);
        if (h == null) return;
        h.moveTo(b.coreX() + 0.5, b.floorY() + 1, b.coreZ() + 0.5, 0, 0);
        h.setup(level.random.nextInt(), HumanJob.NONE, s.culture);
        h.setAge(-24000);
        h.setHome(new BlockPos(b.coreX(), b.floorY() + 1, b.coreZ()));
        h.setSettlement(s.key);
        h.setPersistenceRequired();
        level.addFreshEntity(h);
        s.materialized++;
    }

    /** Coloca ya todas las obras terminadas (carga sus chunks). Para pruebas. Devuelve cuántas quedaron en pie. */
    public static int placeAll(ServerLevel level, Data data, Settlement s) {
        int done = 0;
        for (Settlement.Work w : new ArrayList<>(s.works)) {
            if (w.state < 1) continue;
            for (int cx = (w.b.minX() - 8) >> 4; cx <= (w.b.maxX() + 8) >> 4; cx++) {
                for (int cz = (w.b.minZ() - 8) >> 4; cz <= (w.b.maxZ() + 8) >> 4; cz++) level.getChunk(cx, cz);
            }
            for (int i = 0; i < 200 && w.state < 2 && s.works.contains(w); i++) build(level, data, s, w, 400);
            if (w.state == 2) done++;
        }
        return done;
    }

    // ------------------------------------------------------------------ colonias

    /** Busca tierra libre a 200-320 bloques y funda una colonia con 8 colonos y 300 de cobre del tesoro. */
    public static Settlement foundColony(ServerLevel level, Data data, Settlement parent) {
        Random rng = new Random(parent.seed ^ level.getGameTime());
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        for (int attempt = 0; attempt < 32; attempt++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int dist = 200 + rng.nextInt(121);
            int x = parent.x + (int) (Math.cos(a) * dist), z = parent.z + (int) (Math.sin(a) * dist);
            var biome = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sea), QuartPos.fromBlock(z), rs.sampler());
            Culture culture;
            if (biome.is(VillageSites.DESERT_BIOMES)) culture = Culture.DESERT;
            else if (biome.is(VillageSites.PLAINS_BIOMES)) culture = Culture.PLAINS;
            else continue;
            boolean crowded = false;
            for (Settlement o : data.settlements.values()) {
                if ((long) (o.x - x) * (o.x - x) + (long) (o.z - z) * (o.z - z) < 180L * 180L) crowded = true;
            }
            if (crowded) continue;
            int vr = VillageSites.REGION * 16;
            for (int dx = -1; dx <= 1 && !crowded; dx++) {
                for (int dz = -1; dz <= 1 && !crowded; dz++) {
                    var vs = VillageSites.site(level, Math.floorDiv(x, vr) + dx, Math.floorDiv(z, vr) + dz);
                    if (vs.isPresent() && (long) (vs.get().x() - x) * (vs.get().x() - x) + (long) (vs.get().z() - z) * (vs.get().z() - z) < 200L * 200L) crowded = true;
                }
            }
            if (crowded || VillageSites.nearColiseum(level, x, z)) continue;
            int[] hs = new int[9];
            int wet = 0;
            for (int i = 0; i < 9; i++) {
                double b = i * Math.PI * 2 / 8;
                int r = i == 8 ? 0 : 36;
                int sx = x + (int) (Math.cos(b) * r), sz = z + (int) (Math.sin(b) * r);
                int surf = gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                int floor = gen.getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                hs[i] = surf;
                if (floor < surf || surf <= sea) wet++;
            }
            if (wet > 2 || hs[8] <= sea) continue;
            int[] sorted = hs.clone();
            java.util.Arrays.sort(sorted);
            if (sorted[8] - sorted[0] > 16) continue;

            long seed = rng.nextLong();
            VillageSites.Site site = new VillageSites.Site(x, hs[8], z, seed, culture);
            VillageLayout.Layout lay = VillageLayout.get(level, site);
            if (lay.buildings().isEmpty()) continue;
            Settlement s = new Settlement();
            s.key = VillageBuilder.key(x, z);
            s.x = x;
            s.y = hs[8];
            s.z = z;
            s.seed = seed;
            s.culture = culture;
            s.name = townName(seed, culture);
            s.colony = true;
            s.parent = parent.key;
            s.pop = 8;
            s.food = 40;
            s.treasury = 300;
            s.paved = new boolean[lay.net().size()];
            s.foundedDay = (int) (level.getDayTime() / 24000L);
            parent.pop -= 8;
            parent.treasury -= 300;
            data.settlements.put(s.key, s);
            data.setDirty();
            announce(level, parent, 320, Component.translatable("message.bloodmoon.humanity.colony", parent.name, s.name, x, z));
            BloodMoonMod.LOGGER.info("Humanity: {} founded colony {} at {} {}", parent.name, s.name, x, z);
            return s;
        }
        return null;
    }

    // ------------------------------------------------------------------ utilidades

    static void announce(ServerLevel level, Settlement s, int radius, Component msg) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(s.x, p.getY(), s.z) < (double) radius * radius) p.sendSystemMessage(msg);
        }
    }

    private static final String[] P_A = {"Ash", "Brook", "Oak", "Elm", "Stone", "Mill", "Wolf", "Raven", "Fair", "Green", "Red", "Black",
            "Swan", "Thorn", "Willow", "Hart", "Bram", "Cold", "Kings", "Marsh", "Alder", "Bright", "Hazel", "Iron"};
    private static final String[] P_B = {"ford", "field", "wick", "ton", "bury", "dale", "holm", "stead", "haven", "mere", "brook",
            "wood", "gate", "hill", "moor", "cross"};
    private static final String[] D_A = {"Qasr", "Wadi", "Bir", "Ain", "Ras", "Tell", "Suq", "Dar", "Bab", "Nahr"};
    private static final String[] D_B = {"Zahra", "Amal", "Nur", "Samar", "Rimal", "Hilal", "Jamil", "Qamar", "Sahil", "Yasmin", "Safi", "Badr"};

    public static String townName(long seed, Culture c) {
        Random r = new Random(seed * 0x5DEECE66DL + 11);
        if (c == Culture.DESERT) return D_A[r.nextInt(D_A.length)] + " " + D_B[r.nextInt(D_B.length)];
        return P_A[r.nextInt(P_A.length)] + P_B[r.nextInt(P_B.length)];
    }

    public static Settlement nearest(ServerLevel level, BlockPos p) {
        Settlement best = null;
        double bd = Double.MAX_VALUE;
        for (Settlement s : Data.get(level).settlements.values()) {
            double d = p.distSqr(new BlockPos(s.x, p.getY(), s.z));
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    public static String coins(double copper) {
        long c = (long) copper;
        return (c / 100) + "o " + (c / 10 % 10) + "p " + (c % 10) + "c";
    }

    public static String describe(ServerLevel level, Settlement s) {
        List<VillageLayout.Building> bs = built(level, s);
        Settlement.Work cur = s.current();
        int housing = 0;
        for (VillageLayout.Building b : bs) housing += capacity(b);
        double prod = production(bs, s.pop);
        return s.name + " (" + (s.level == Settlement.TOWN ? "pueblo" : "aldea") + (s.colony ? ", colonia" : "") + ", " + s.culture + ") en "
                + s.x + " " + s.z + "\n población " + s.pop + "/" + housing + " (" + s.materialized + " a la vista)"
                + " · comida " + (int) s.food + " (" + String.format(java.util.Locale.ROOT, "%+.1f", prod - s.pop) + "/día)"
                + " · tesoro " + coins(s.treasury)
                + "\n edificios " + bs.size() + " · obra: " + (cur == null ? "ninguna" : cur.b.template() + " " + (int) (s.progress * 100) + "%")
                + " · pendientes de colocar " + s.works.stream().filter(w -> w.state == 1).count();
    }
}

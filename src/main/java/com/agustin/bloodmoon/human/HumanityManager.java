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
    public static final int TOWN_POP = 40, COLONY_POP = 60, CITY_POP = 100, CAPITAL_POP = 220;

    static int threshold(int level) {
        return switch (level) {
            case Settlement.CAPITAL -> CAPITAL_POP;
            case Settlement.CITY -> CITY_POP;
            case Settlement.TOWN -> TOWN_POP;
            default -> 0;
        };
    }

    private static final ConcurrentLinkedQueue<Long> REGISTER = new ConcurrentLinkedQueue<>();
    private static int timer;
    private static long lastCaravanDay = -1;

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
        Port.clear();
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
            long day = level.getDayTime() / 24000L;
            if (day != lastCaravanDay) {
                lastCaravanDay = day;
                Market.caravans(level, data);
            }
            data.setDirty();
        }
        int budget = 400;
        for (Settlement s : data.settlements.values()) {
            if (s.streetTier >= 1) upgradeStreets(level, data, s);
            if (!s.wall.isEmpty()) buildWall(level, data, s);
            if (s.portPiers > 0) Port.build(level, data, s);
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

    static boolean city(VillageLayout.Building b) {
        String t = b.template();
        return t.contains("/city_") || t.endsWith("/market") || t.endsWith("/hall") || t.endsWith("/castle") || t.endsWith("/fountain");
    }

    static int capacity(VillageLayout.Building b) {
        boolean c = city(b);
        return switch (b.kind()) {
            case HOUSE -> c ? 8 : b.template().contains("large") ? 5 : 3;
            case WORK -> c ? 3 : 1;
            case TOWER -> c ? 4 : 2;
            case CASTLE -> 8;
            case HALL -> 2;
            default -> 0;
        };
    }

    /** Edificios que cuentan (en pie o terminados como datos), sin los demolidos por la renovación. */
    public static List<VillageLayout.Building> built(ServerLevel level, Settlement s) {
        List<VillageLayout.Building> out = new ArrayList<>();
        if (!s.colony) {
            List<VillageLayout.Building> init = VillageLayout.get(level, s.site()).buildings();
            for (int i = 0; i < init.size(); i++) if (i >= s.removed.length || !s.removed[i]) out.add(init.get(i));
        }
        for (Settlement.Work w : s.works) if (w.state >= 1 && w.state < 3 && !w.demolishOnly) out.add(w.b);
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
        boolean c = city(b);
        if (b.template().endsWith("city_work_cleric")) return 2500;
        return switch (b.kind()) {
            case HOUSE -> c ? 600 : b.template().contains("large") ? 260 : 150;
            case WORK -> c ? 700 : 220;
            case TOWER -> c ? 800 : 400;
            case STALL -> 80;
            case FARM -> 100;
            case WELL -> c ? 300 : 50;
            case MARKET -> 900;
            case HALL -> 1500;
            case CASTLE -> 4000;
        };
    }

    static double days(VillageLayout.Building b) {
        boolean c = city(b);
        if (b.template().endsWith("city_work_cleric")) return 8;
        return switch (b.kind()) {
            case HOUSE -> c ? 2.5 : b.template().contains("large") ? 1.6 : 1.0;
            case WORK -> c ? 2.5 : 1.2;
            case TOWER -> c ? 3.0 : 2.0;
            case STALL -> 0.5;
            case FARM -> 0.6;
            case WELL -> c ? 1.0 : 0.4;
            case MARKET -> 3;
            case HALL -> 5;
            case CASTLE -> 10;
        };
    }

    public static void cycle(ServerLevel level, Data data, Settlement s) {
        List<VillageLayout.Building> bs = built(level, s);
        int housing = 0;
        for (VillageLayout.Building b : bs) housing += capacity(b);
        Market.cycle(s, bs);
        // puerto: se busca agua una vez; los muelles crecen con el nivel mientras el agua lo permita
        if (!s.portChecked) Port.plan(level, s);
        if (Port.has(s) && s.portPiers < Port.wantPiers(s) && s.treasury >= 250) {
            s.treasury -= 250;
            s.portPiers++;
        }
        double prod = (production(bs, s.pop) + s.portPiers * 6.0) * Market.toolFactor(s, bs), cons = s.pop;
        s.food += (prod - cons) / CYCLES_PER_DAY;
        if (s.food < 0) {
            // hambre: algunos se van
            if (level.random.nextInt(4) == 0 && s.pop > 4) s.pop--;
            s.food = 0;
        }
        s.food = Math.min(s.food, Math.max(20, s.pop * 20.0));
        // nacimientos
        if (s.food > s.pop * 2.0 && s.pop < housing) {
            s.growth += s.pop * 0.03 * Market.comfortFactor(s, bs) / CYCLES_PER_DAY;
            while (s.growth >= 1 && s.pop < housing) {
                s.growth -= 1;
                s.pop++;
                birth(level, s, bs);
            }
        }
        // tesoro
        s.treasury += (s.pop * 2.5 + count(bs, VillageLayout.Kind.STALL) * 8 + count(bs, VillageLayout.Kind.MARKET) * 40
                - count(bs, VillageLayout.Kind.TOWER) * 6 - count(bs, VillageLayout.Kind.CASTLE) * 20 - (s.wall.isEmpty() ? 0 : 10)) / CYCLES_PER_DAY;
        if (s.treasury < 0) s.treasury = 0;
        // nivel
        int oldLevel = s.level;
        int target = s.pop >= CAPITAL_POP ? Settlement.CAPITAL : s.pop >= CITY_POP ? Settlement.CITY : s.pop >= TOWN_POP ? Settlement.TOWN : Settlement.VILLAGE;
        // se sube enseguida; se baja recién con un 25 % menos de gente (no se deshace una ciudad por una mala semana)
        if (target > s.level) s.level = target;
        else if (target < s.level && s.pop < threshold(s.level) * 3 / 4) s.level = target;
        if (s.level != oldLevel) {
            String key = switch (s.level) {
                case Settlement.CAPITAL -> "message.bloodmoon.humanity.capital";
                case Settlement.CITY -> "message.bloodmoon.humanity.city";
                case Settlement.TOWN -> "message.bloodmoon.humanity.town";
                default -> "message.bloodmoon.humanity.village";
            };
            announce(level, s, 400, Component.translatable(key, s.name));
            if (s.level >= Settlement.CITY && s.streetTier == 0) s.streetTier = 1;   // empiezan a empedrar
        }
        // muralla de ciudad: se planifica una vez y avanza como datos
        if (s.level >= Settlement.CITY && s.wall.isEmpty() && s.treasury >= 3000) {
            planWall(level, s, bs);
            if (!s.wall.isEmpty()) s.treasury -= 3000;
        }
        // la ciudad desbordó la muralla: otro anillo por fuera
        if (!s.wall.isEmpty() && s.wallProgress >= 1 && s.treasury >= 5000 && outsideWall(s, bs) > 8) {
            int before = s.wall.size();
            planWall(level, s, bs);
            if (s.wall.size() > before) s.treasury -= 5000;
        }
        if (!s.wall.isEmpty() && s.wallProgress < 1) s.wallProgress = Math.min(1, s.wallProgress + 1.0 / 6 / CYCLES_PER_DAY);
        // obras: varias a la vez según el nivel (una aldea levanta una casa; una ciudad rica, varias)
        boolean mason = false;
        for (VillageLayout.Building b : bs) if (b.job() == HumanJob.MASON) mason = true;
        int maxActive = 1 + s.level + (mason ? 1 : 0);
        for (int tries = 0; tries < maxActive; tries++) {
            int active = 0;
            for (Settlement.Work w : s.works) if (w.state == 0) active++;
            if (active >= maxActive) break;
            int before = s.works.size();
            planNext(level, s, bs, housing, prod, cons);
            if (s.works.size() == before) break;
        }
        for (Settlement.Work w : s.works) {
            if (w.state != 0) continue;
            w.progress += (1.0 / days(w.b)) * (mason ? 1.3 : 1.0) / CYCLES_PER_DAY;
            if (w.progress >= 1) {
                w.progress = 1;
                w.state = 1;
                if (!w.demolishOnly) s.pop += w.b.residents();   // llegan los trabajadores del edificio nuevo
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
        // granjas si falta comida (con el granero lleno no se frena todo lo demás por una granja que no encuentra lote)
        // la ciudad se renueva antes que nada mientras no haya hambre
        if (s.level >= Settlement.CITY && s.food > s.pop * 3.0 && renewal(level, s, lay, bs, c, rng)) return;
        if (prod < cons * 1.15 && s.food < s.pop * 10.0) {
            template = c + "farm_" + rng.nextInt(2);
            kind = VillageLayout.Kind.FARM;
        } else if (s.pop >= housing - 1) {
            if (s.level >= Settlement.CITY) {
                template = c + "city_house_" + rng.nextInt(s.culture == Culture.DESERT ? 4 : 5);
            } else {
                boolean large = s.pop > 30 && rng.nextBoolean();
                template = c + (large ? "house_large_" : "house_small_") + rng.nextInt(pals);
            }
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
            template = c + (s.level >= Settlement.CITY ? "city_work_" : "work_") + job.vanilla;
            kind = VillageLayout.Kind.WORK;
            residents = 1;
        }
        // presupuesto
        int price = cost(new VillageLayout.Building(template, kind, job, residents, 0, 0, net.minecraft.world.level.block.Rotation.NONE,
                0, 0, 0, 0, 0, 0, 0));
        double imports = Market.importCost(s, new VillageLayout.Building(template, kind, job, residents, 0, 0,
                net.minecraft.world.level.block.Rotation.NONE, 0, 0, 0, 0, 0, 0, 0));
        if (s.treasury < price + imports) return;
        if (s.plotWait > 0) {
            s.plotWait--;
            return;
        }
        // lote
        List<VillageLayout.Building> others = occupied(level, s, lay, null);
        Net nn = net(s, lay);
        List<VillageLayout.Road> roads = nn.blocking(lay.plaza(), s);
        java.util.function.Predicate<VillageLayout.Building> edge = kind == VillageLayout.Kind.TOWER ? VillageLayout.periphery(s.x, s.z, bs) : null;
        VillageLayout.Plot pl = VillageLayout.findPlot(level, s.site(), nn.roads(), nn.dist(), 1000, template, kind, job,
                residents, others, roads, b -> (edge == null || edge.test(b)) && (!loaded(level, b) || VillageBuilder.artificial(level, b, 3) <= 3), true);
        if (pl == null) {
            // no hay lugar: se abren calles nuevas y se vuelve a mirar en un rato
            s.plotWait = extendNetwork(level, s, lay) > 0 ? 2 : CYCLES_PER_DAY;
            return;
        }
        s.treasury -= price + imports;
        Market.consumeMaterials(s, pl.building());
        Settlement.Work w = new Settlement.Work();
        w.b = pl.building();
        w.spur = pl.spur();
        w.arm = pl.seg();
        w.d = pl.t();
        s.works.add(w);
    }

    /** Red de calles completa: la del plano más las que se abrieron al crecer. */
    record Net(List<VillageLayout.Road> roads, int[] parent, double[] dist) {
        int size() {
            return roads.size();
        }

        /** Calles que bloquean lotes: toda la red y la plaza. */
        List<VillageLayout.Road> blocking(VillageLayout.Road plaza, Settlement s) {
            List<VillageLayout.Road> out = new ArrayList<>(roads);
            out.add(plaza);
            // la muralla (y una franja de 4 bloques a cada lado) tampoco se pisa
            for (VillageLayout.Road w : s.wall) out.add(new VillageLayout.Road(w.x0(), w.z0(), w.x1(), w.z1(), 5.5));
            return out;
        }
    }

    static Net net(Settlement s, VillageLayout.Layout lay) {
        int n0 = lay.net().size(), n1 = s.extraNet.size();
        List<VillageLayout.Road> roads = new ArrayList<>(n0 + n1);
        roads.addAll(lay.net());
        roads.addAll(s.extraNet);
        int[] parent = java.util.Arrays.copyOf(lay.parent(), n0 + n1);
        double[] dist = java.util.Arrays.copyOf(lay.dist(), n0 + n1);
        for (int i = 0; i < n1; i++) {
            parent[n0 + i] = i < s.extraParent.length ? s.extraParent[i] : -1;
            dist[n0 + i] = i < s.extraDist.length ? s.extraDist[i] : 200;
        }
        if (s.paved.length < n0 + n1) s.paved = java.util.Arrays.copyOf(s.paved, n0 + n1);
        return new Net(roads, parent, dist);
    }

    /** Sin lotes libres: el asentamiento abre dos calles nuevas desde las más alejadas. */
    static int extendNetwork(ServerLevel level, Settlement s, VillageLayout.Layout lay) {
        if (s.extraNet.size() > 260) return 0;
        Net n = net(s, lay);
        List<StreetPlanner.Seg> all = new ArrayList<>();
        for (int i = 0; i < n.size(); i++) {
            VillageLayout.Road r = n.roads().get(i);
            all.add(new StreetPlanner.Seg(r.x0(), r.z0(), r.x1(), r.z1(), r.half(), n.parent()[i], n.dist()[i], r.y0(), r.y1()));
        }
        int maxR = Math.min(260, 130 + s.extraNet.size() / 2);
        int added = StreetPlanner.extend(all, s.seed, s.x, s.z, VillageLayout.terrain(level), maxR, 2);
        if (added == 0) return 0;
        int[] par = java.util.Arrays.copyOf(s.extraParent, s.extraParent.length + added);
        double[] dd = java.util.Arrays.copyOf(s.extraDist, s.extraDist.length + added);
        for (int i = all.size() - added; i < all.size(); i++) {
            StreetPlanner.Seg g = all.get(i);
            s.extraNet.add(new VillageLayout.Road(g.x0(), g.z0(), g.x1(), g.z1(), g.half(), g.y0(), g.y1()));
            int k = s.extraNet.size() - 1;
            par[k] = g.parent();
            dd[k] = g.dist();
        }
        s.extraParent = par;
        s.extraDist = dd;
        return added;
    }

    /** Huellas ocupadas (edificios en pie, obras y lotes vetados), salvo {@code except}. */
    static List<VillageLayout.Building> occupied(ServerLevel level, Settlement s, VillageLayout.Layout lay, VillageLayout.Building except) {
        List<VillageLayout.Building> out = new ArrayList<>();
        for (int i = 0; i < lay.buildings().size(); i++) {
            VillageLayout.Building b = lay.buildings().get(i);
            if (b == except) continue;
            boolean gone = i < s.removed.length && s.removed[i];
            if (!gone || s.colony) out.add(b);
        }
        for (Settlement.Work w : s.works) if (w.state < 3 && w.b != except && !w.demolishOnly) out.add(w.b);
        out.addAll(s.blocked);
        return out;
    }

    /** Un edificio viejo candidato a renovarse: del plano original ({@code index} >= 0) o una obra ({@code work}). */
    private record Old(VillageLayout.Building b, int index, Settlement.Work work, double dist) {}

    /**
     * Ciudad: los edificios cívicos (fuente, ayuntamiento, mercado, castillo en la capital) y la renovación del centro —
     * casas, talleres y torres de madera se demuelen y en su lugar se levantan edificios de piedra de varias plantas;
     * las granjas del centro se mudan afuera. Devuelve true si ya decidió (aunque espere a juntar el dinero).
     */
    /** Diagnóstico de la renovación (pruebas): {llamadas, fuente, cívico en lugar viejo, cívico en lote, casa, granja, no entra}. */
    public static final int[] RENEW = new int[7];

    private static boolean renewal(ServerLevel level, Settlement s, VillageLayout.Layout lay, List<VillageLayout.Building> bs, String c, Random rng) {
        RENEW[0]++;
        Net nn = net(s, lay);
        List<VillageLayout.Road> roads = nn.blocking(lay.plaza(), s);
        // candidatos viejos, del centro hacia afuera
        List<Old> olds = new ArrayList<>();
        if (!s.colony) {
            for (int i = 0; i < lay.buildings().size(); i++) {
                VillageLayout.Building b = lay.buildings().get(i);
                if (i < s.removed.length && s.removed[i]) continue;
                olds.add(new Old(b, i, null, Math.hypot(b.x() - s.x, b.z() - s.z)));
            }
        }
        for (Settlement.Work w : s.works) {
            if (w.state != 2 || w.demolishOnly || city(w.b)) continue;
            olds.add(new Old(w.b, -1, w, Math.hypot(w.b.x() - s.x, w.b.z() - s.z)));
        }
        olds.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        boolean hasFountain = false, hasHall = false, hasMarket = false, hasCastle = false;
        for (VillageLayout.Building b : bs) {
            if (b.template().endsWith("/fountain")) hasFountain = true;
            if (b.kind() == VillageLayout.Kind.HALL) hasHall = true;
            if (b.kind() == VillageLayout.Kind.MARKET) hasMarket = true;
            if (b.kind() == VillageLayout.Kind.CASTLE) hasCastle = true;
        }
        for (Settlement.Work w : s.works) {
            if (w.state >= 3) continue;
            if (w.b.template().endsWith("/fountain")) hasFountain = true;
            if (w.b.kind() == VillageLayout.Kind.HALL) hasHall = true;
            if (w.b.kind() == VillageLayout.Kind.MARKET) hasMarket = true;
            if (w.b.kind() == VillageLayout.Kind.CASTLE) hasCastle = true;
        }
        // 1. la fuente reemplaza al pozo
        if (!hasFountain) {
            for (Old o : olds) {
                if (o.b().kind() != VillageLayout.Kind.WELL) continue;
                VillageLayout.Building nb = VillageLayout.placeAt(level, c + "fountain", VillageLayout.Kind.WELL, HumanJob.NONE, 0,
                        o.b().coreX(), o.b().coreZ(), o.b().rot(), o.b().floorY(), occupied(level, s, lay, o.b()), List.of(), 0);
                if (nb != null) { RENEW[1]++; return replace(s, lay, o, nb, false); }
            }
        }
        // 2-4. cívicos: primero ocupando el lugar de algo viejo cerca del centro, si no en un lote libre
        String[][] civic = {
                {hasHall ? null : c + "hall", "HALL", "GUARD", "2"},
                {hasMarket ? null : c + "market", "MARKET", "MERCHANT", "3"},
                {hasCastle || s.level < Settlement.CAPITAL ? null : c + "castle", "CASTLE", "GUARD", "4"}};
        for (String[] cv : civic) {
            if (cv[0] == null) continue;
            VillageLayout.Kind kind = VillageLayout.Kind.valueOf(cv[1]);
            HumanJob job = HumanJob.valueOf(cv[2]);
            int res = Integer.parseInt(cv[3]);
            if (kind != VillageLayout.Kind.CASTLE) {
                for (Old o : olds) {
                    if (o.dist() > 60 || city(o.b()) || o.b().kind() == VillageLayout.Kind.WELL) continue;
                    VillageLayout.Building nb = VillageLayout.placeAt(level, cv[0], kind, job, res, o.b().coreX(), o.b().coreZ(), o.b().rot(),
                            o.b().floorY(), occupied(level, s, lay, o.b()), roads, 1);
                    if (nb != null) { RENEW[2]++; return replace(s, lay, o, nb, false); }
                }
            }
            VillageLayout.Plot pl = VillageLayout.findPlot(level, s.site(), nn.roads(), nn.dist(), 1000, cv[0], kind, job, res,
                    occupied(level, s, lay, null), roads, b -> !loaded(level, b) || VillageBuilder.artificial(level, b, 3) <= 3, true);
            if (pl != null) {
                RENEW[3]++;
                VillageLayout.Building nb = pl.building();
                double price = cost(nb) + Market.importCost(s, nb);
                if (s.treasury < price) return true;
                s.treasury -= price;
                Market.consumeMaterials(s, nb);
                Settlement.Work w = new Settlement.Work();
                w.b = nb;
                w.spur = pl.spur();
                w.arm = pl.seg();
                s.works.add(w);
                return true;
            }
        }
        // 5. renovación: lo viejo del centro se reemplaza por piedra; las granjas del centro se van
        int pals = s.culture == Culture.DESERT ? 4 : 5;
        for (Old o : olds) {
            if (o.dist() > 80 || city(o.b())) continue;
            VillageLayout.Building b = o.b();
            if (s.skipRenew.contains(VillageBuilder.key(b.coreX(), b.coreZ()))) continue;
            String nt = switch (b.kind()) {
                case HOUSE -> c + "city_house_" + rng.nextInt(pals);
                case WORK -> c + "city_work_" + b.job().vanilla;
                case TOWER -> c + "city_tower";
                default -> null;
            };
            if (b.kind() == VillageLayout.Kind.FARM && o.dist() < 75 && s.food > s.pop * 6.0) {   // sin granero lleno, la granja se queda
                RENEW[5]++;
                return replace(s, lay, o, b, true);
            }
            if (nt == null) continue;
            int res = b.kind() == VillageLayout.Kind.TOWER ? 3 : b.kind() == VillageLayout.Kind.WORK ? 1 : 0;
            VillageLayout.Building nb = VillageLayout.placeAt(level, nt, b.kind(), b.job(), res, b.coreX(), b.coreZ(), b.rot(), b.floorY(),
                    occupied(level, s, lay, b), roads, 1);
            // una casa prueba las otras fachadas (cada una con su planta) antes de rendirse
            for (int k = 1; nb == null && b.kind() == VillageLayout.Kind.HOUSE && k < pals; k++) {
                nt = c + "city_house_" + Math.floorMod(nt.charAt(nt.length() - 1) - '0' + 1, pals);
                nb = VillageLayout.placeAt(level, nt, b.kind(), b.job(), res, b.coreX(), b.coreZ(), b.rot(), b.floorY(),
                        occupied(level, s, lay, b), roads, 1);
            }
            if (nb == null) {
                s.skipRenew.add(VillageBuilder.key(b.coreX(), b.coreZ()));   // no entra: queda como está
                RENEW[6]++;
                continue;
            }
            RENEW[4]++;
            return replace(s, lay, o, nb, false);
        }
        return false;
    }

    /** Crea la obra que reemplaza (o solo demuele) {@code o}; el viejo deja de contar ya. */
    private static boolean replace(Settlement s, VillageLayout.Layout lay, Old o, VillageLayout.Building nb, boolean demolishOnly) {
        double price = demolishOnly ? 30 : cost(nb) + Market.importCost(s, nb);
        if (s.treasury < price) return true;
        s.treasury -= price;
        if (!demolishOnly) Market.consumeMaterials(s, nb);
        Settlement.Work w = new Settlement.Work();
        w.b = nb;
        w.demolishOnly = demolishOnly;
        if (o.index() >= 0) {
            if (s.removed.length < lay.buildings().size()) s.removed = java.util.Arrays.copyOf(s.removed, lay.buildings().size());
            s.removed[o.index()] = true;
            w.replaces = o.index();
            w.arm = o.index() < lay.buildingSeg().length ? lay.buildingSeg()[o.index()] : -1;
            w.spur = o.index() < lay.spurs().size() ? lay.spurs().get(o.index()) : null;
        } else {
            w.replaces = -(s.works.indexOf(o.work()) + 2);
            o.work().state = 3;
            w.arm = o.work().arm;
            w.spur = o.work().spur;
        }
        s.works.add(w);
        return true;
    }

    /** El edificio que una obra reemplaza, o null. */
    static VillageLayout.Building replaced(ServerLevel level, Settlement s, Settlement.Work w) {
        if (w.replaces >= 0) {
            List<VillageLayout.Building> init = VillageLayout.get(level, s.site()).buildings();
            return w.replaces < init.size() ? init.get(w.replaces) : null;
        }
        if (w.replaces <= -2) {
            int i = -w.replaces - 2;
            return i < s.works.size() ? s.works.get(i).b : null;
        }
        return null;
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
            VillageLayout.Building old = replaced(level, s, w);
            if (old == null && VillageBuilder.artificial(level, w.b, 3) > 3) {
                // el jugador construyó en el lote: se descarta y se devuelve el dinero
                w.state = 4;
                s.blocked.add(w.b);
                s.treasury += cost(w.b);
                data.setDirty();
                return 1;
            }
            if (old != null) {
                if (!loaded(level, old)) return 0;
                VillageBuilder.demolish(level, level, old, desert);
            }
            if (w.demolishOnly) {
                w.prepared = true;
                if (w.state >= 1) w.state = 2;
                data.setDirty();
                return 40;
            }
            VillageLayout.Layout lay = VillageLayout.get(level, s.site());
            int[] ybox = {w.b.minX() - 3, w.b.minZ() - 3, w.b.maxX() + 3, w.b.maxZ() + 3};
            VillageBuilder.clearTrees(level, ybox, List.of(w.b), List.of(), occupied(level, s, VillageLayout.get(level, s.site()), w.b), false);
            List<VillageLayout.Road> near = new ArrayList<>();
            near.add(lay.plaza());
            Net nn = net(s, lay);
            for (int k = 0; k < nn.size(); k++) if (s.paved[k]) near.add(nn.roads().get(k));
            VillageBuilder.yard(level, w.b, ybox, near, occupied(level, s, lay, w.b), desert, false);
            VillageBuilder.prepare(level, level, w.b, box, desert);
            w.prepared = true;
            if (w.state == 0 && level.getNearestPlayer(w.b.coreX(), w.b.floorY(), w.b.coreZ(), 96, false) != null) spawnBuilder(level, s, w);
            data.setDirty();
            return 40;
        }
        if (w.demolishOnly) {
            if (w.state >= 1) w.state = 2;
            return 1;
        }
        int size = VillageBuilder.size(level, w.b);
        int target = w.state >= 1 ? size : (int) Math.floor(w.progress * size);
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
        Net nn = net(s, lay);
        boolean[] fresh = new boolean[nn.size()];
        for (int k = w.arm; k >= 0 && k < fresh.length && !s.paved[k]; k = nn.parent()[k]) {
            s.paved[k] = true;
            fresh[k] = true;
            roads.add(nn.roads().get(k));
        }
        // una colonia pavimenta su plaza con el pozo
        if (w.b.kind() == VillageLayout.Kind.WELL) roads.add(lay.plaza());
        boolean stoneNow = s.streetTier >= 1;
        List<VillageLayout.Building> all = new ArrayList<>(built(level, s));
        for (VillageLayout.Road r : roads) {
            int[] box = {(int) Math.floor(Math.min(r.x0(), r.x1()) - r.half() - 3), (int) Math.floor(Math.min(r.z0(), r.z1()) - r.half() - 3),
                    (int) Math.ceil(Math.max(r.x0(), r.x1()) + r.half() + 3), (int) Math.ceil(Math.max(r.z0(), r.z1()) + r.half() + 3)};
            VillageBuilder.clearTrees(level, box, List.of(), List.of(r), all, false);
            if (!s.wall.isEmpty()) gates(level, s, r);
            VillageBuilder.pave(level, box, List.of(r), all, desert, false, stoneNow);
        }
        if (stoneNow) {
            if (s.stone.length < nn.size()) s.stone = java.util.Arrays.copyOf(s.stone, nn.size());
            for (int k = 0; k < fresh.length; k++) if (fresh[k]) s.stone[k] = true;
        }
        for (int[] l : VillageLayout.lamps(nn.roads(), fresh, all, s.works.size(), s.streetTier >= 1 ? 9 : 12, s.streetTier >= 1)) {
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

    // ------------------------------------------------------------------ ciudad: calles empedradas y muralla

    /** Empiedra un tramo pavimentado por tick (si sus chunks están cargados) y le suma faroles a los dos lados. */
    private static void upgradeStreets(ServerLevel level, Data data, Settlement s) {
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        Net nn = net(s, lay);
        int n = nn.size();
        if (s.stone.length != n) s.stone = java.util.Arrays.copyOf(s.stone, n);
        for (int i = -1; i < n; i++) {
            if (i < 0 ? s.plazaStone : (s.stone[i] || !s.paved[i])) continue;
            VillageLayout.Road r = i < 0 ? lay.plaza() : nn.roads().get(i);
            int[] box = {(int) Math.floor(Math.min(r.x0(), r.x1()) - r.half() - 3), (int) Math.floor(Math.min(r.z0(), r.z1()) - r.half() - 3),
                    (int) Math.ceil(Math.max(r.x0(), r.x1()) + r.half() + 3), (int) Math.ceil(Math.max(r.z0(), r.z1()) + r.half() + 3)};
            if (!loadedBox(level, box)) continue;
            boolean desert = s.culture == Culture.DESERT;
            List<VillageLayout.Building> all = built(level, s);
            if (!s.wall.isEmpty()) gates(level, s, r);
            VillageBuilder.pave(level, box, List.of(r), all, desert, false, true);
            if (i >= 0) {
                boolean[] one = new boolean[n];
                one[i] = true;
                for (int[] l : VillageLayout.lamps(nn.roads(), one, all, i, 9, true)) {
                    if (level.hasChunk(l[0] >> 4, l[1] >> 4)) VillageBuilder.lamp(level, l[0], l[1], desert, false);
                }
                s.stone[i] = true;
            } else {
                s.plazaStone = true;
            }
            data.setDirty();
            return;
        }
    }

    static final int WALL_RAYS = 48;

    /** Radio del anillo exterior de la muralla en cada rayo (vacío si no hay muralla). */
    static double[] outerRing(Settlement s) {
        int n = WALL_RAYS;
        if (s.wall.size() < n) return new double[0];
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            VillageLayout.Road g = s.wall.get(s.wall.size() - n + i);
            r[i] = Math.hypot(g.x0() - s.x, g.z0() - s.z);
        }
        return r;
    }

    private static int ray(Settlement s, double x, double z) {
        double a = Math.atan2(z - s.z, x - s.x);
        return Math.floorMod((int) Math.round(a / (Math.PI * 2) * WALL_RAYS), WALL_RAYS);
    }

    /** Edificios que quedaron fuera del anillo exterior (o pegados a él). */
    static int outsideWall(Settlement s, List<VillageLayout.Building> bs) {
        double[] r = outerRing(s);
        if (r.length == 0) return 0;
        int out = 0;
        for (VillageLayout.Building b : bs) {
            double d = Math.hypot(b.x() - s.x, b.z() - s.z);
            if (d > r[ray(s, b.x(), b.z())] - 6) out++;
        }
        return out;
    }

    /**
     * Traza un anillo de muralla (48 rayos) a 20 bloques de todo lo construido, nunca a través de un edificio; si ya hay
     * muralla, el anillo nuevo va por fuera del anterior (al menos 24 bloques más lejos). Las puertas se abren solas donde
     * cruza una calle, con arco y torres a los lados.
     */
    static void planWall(ServerLevel level, Settlement s, List<VillageLayout.Building> bs) {
        int n = WALL_RAYS;
        double[] prev = outerRing(s);
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = prev.length == 0 ? 48 : prev[i] + 24;
        for (VillageLayout.Building b : bs) {
            for (int k = 0; k < 4; k++) {
                int x = (k & 1) == 0 ? b.minX() : b.maxX(), z = (k & 2) == 0 ? b.minZ() : b.maxZ();
                int i = ray(s, x, z);
                double d = Math.hypot(x - s.x, z - s.z) + 20;
                for (int j = -2; j <= 2; j++) r[Math.floorMod(i + j, n)] = Math.max(r[Math.floorMod(i + j, n)], d);
            }
        }
        // suavizado hacia afuera: nunca entra más que el rayo crudo
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) sm[i] = Math.max(r[i], (r[Math.floorMod(i - 1, n)] + r[i] * 2 + r[(i + 1) % n]) / 4);
        double max = 0;
        for (double v : sm) max = Math.max(max, v);
        if (max > 340) return;
        int base = s.wall.size();
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            s.wall.add(new VillageLayout.Road(s.x + Math.cos(a0) * sm[i], s.z + Math.sin(a0) * sm[i],
                    s.x + Math.cos(a1) * sm[(i + 1) % n], s.z + Math.sin(a1) * sm[(i + 1) % n], 1.5));
        }
        s.wallDone = java.util.Arrays.copyOf(s.wallDone, s.wall.size());
        s.wallProgress = (double) base / s.wall.size();
    }

    /** Abre una puerta en los tramos de muralla ya levantados que cruza una calle nueva. */
    private static void gates(ServerLevel level, Settlement s, VillageLayout.Road road) {
        for (int i = 0; i < s.wall.size() && i < s.wallDone.length; i++) {
            if (!s.wallDone[i]) continue;
            VillageLayout.Road w = s.wall.get(i);
            double pad = road.half() + 3;
            if (Math.max(w.x0(), w.x1()) < Math.min(road.x0(), road.x1()) - pad || Math.min(w.x0(), w.x1()) > Math.max(road.x0(), road.x1()) + pad
                    || Math.max(w.z0(), w.z1()) < Math.min(road.z0(), road.z1()) - pad || Math.min(w.z0(), w.z1()) > Math.max(road.z0(), road.z1()) + pad) continue;
            VillageBuilder.gate(level, w, road, s.culture == Culture.DESERT);
        }
    }

    private static void buildWall(ServerLevel level, Data data, Settlement s) {
        int n = s.wall.size();
        if (s.wallDone.length != n) s.wallDone = java.util.Arrays.copyOf(s.wallDone, n);
        int upTo = (int) Math.floor(s.wallProgress * n);
        for (int i = 0; i < Math.min(upTo, n); i++) {
            if (s.wallDone[i]) continue;
            VillageLayout.Road r = s.wall.get(i);
            int[] box = {(int) Math.floor(Math.min(r.x0(), r.x1())) - 4, (int) Math.floor(Math.min(r.z0(), r.z1())) - 4,
                    (int) Math.ceil(Math.max(r.x0(), r.x1())) + 4, (int) Math.ceil(Math.max(r.z0(), r.z1())) + 4};
            if (!loadedBox(level, box)) continue;
            VillageLayout.Layout lay = VillageLayout.get(level, s.site());
            List<VillageLayout.Road> streets = new ArrayList<>();
            Net nn = net(s, lay);
            for (int k = 0; k < nn.size(); k++) if (s.paved[k]) streets.add(nn.roads().get(k));
            VillageBuilder.wall(level, r, s.x, s.z, (i % WALL_RAYS) % 4 == 0, streets, built(level, s), s.culture == Culture.DESERT);
            s.wallDone[i] = true;
            data.setDirty();
            return;
        }
    }

    /** Para pruebas: carga lo necesario y termina de empedrar y amurallar ya. Devuelve {tramos empedrados, tramos de muralla}. */
    public static int[] finishCity(ServerLevel level, Data data, Settlement s) {
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        for (VillageLayout.Road r : net(s, lay).roads()) {
            for (int cx = ((int) Math.min(r.x0(), r.x1()) - 6) >> 4; cx <= ((int) Math.max(r.x0(), r.x1()) + 6) >> 4; cx++) {
                for (int cz = ((int) Math.min(r.z0(), r.z1()) - 6) >> 4; cz <= ((int) Math.max(r.z0(), r.z1()) + 6) >> 4; cz++) level.getChunk(cx, cz);
            }
        }
        for (VillageLayout.Road r : s.wall) {
            for (int cx = ((int) Math.min(r.x0(), r.x1()) - 5) >> 4; cx <= ((int) Math.max(r.x0(), r.x1()) + 5) >> 4; cx++) {
                for (int cz = ((int) Math.min(r.z0(), r.z1()) - 5) >> 4; cz <= ((int) Math.max(r.z0(), r.z1()) + 5) >> 4; cz++) level.getChunk(cx, cz);
            }
        }
        s.wallProgress = s.wall.isEmpty() ? 0 : 1;
        for (int i = 0; i < 600; i++) {
            if (s.streetTier >= 1) upgradeStreets(level, data, s);
            if (!s.wall.isEmpty()) buildWall(level, data, s);
        }
        int st = 0, wd = 0;
        for (boolean b : s.stone) if (b) st++;
        for (boolean b : s.wallDone) if (b) wd++;
        return new int[]{st, wd};
    }

    // ------------------------------------------------------------------ mapa

    /** Radio de influencia: hasta el edificio más lejano + 24 bloques (entre 48 y 320). */
    public static int influence(ServerLevel level, Settlement s) {
        double r = 0;
        for (VillageLayout.Building b : built(level, s)) {
            for (int k = 0; k < 4; k++) {
                int x = (k & 1) == 0 ? b.minX() : b.maxX(), z = (k & 2) == 0 ? b.minZ() : b.maxZ();
                r = Math.max(r, Math.hypot(x - s.x, z - s.z));
            }
        }
        return (int) Math.max(48, Math.min(320, r + 24));
    }

    /** Color propio de cada asentamiento (tono por clave; cálido en el desierto, fresco en la llanura). */
    public static int color(Settlement s) {
        long h = s.key * 0x9E3779B97F4A7C15L;
        float hue = s.culture == Culture.DESERT ? 0.02F + ((h >>> 40) & 0xFF) / 255F * 0.13F : 0.25F + ((h >>> 40) & 0xFF) / 255F * 0.45F;
        return java.awt.Color.HSBtoRGB(hue, 0.65F, 0.9F) & 0xFFFFFF;
    }

    /** Soldados del asentamiento: los guardias que viven en sus torres, ayuntamiento y castillo. */
    public static int soldiers(ServerLevel level, Settlement s) {
        int n = 0;
        for (VillageLayout.Building b : built(level, s)) if (b.job() == HumanJob.GUARD) n += b.residents();
        return Math.min(n, s.pop);
    }

    public static void sendMap(ServerPlayer player) {
        ServerLevel ow = player.server.overworld();
        if (player.level() != ow) return;
        List<com.agustin.bloodmoon.network.SettlementMapPayload.View> views = new ArrayList<>();
        for (Settlement s : Data.get(ow).settlements.values()) {
            if (player.distanceToSqr(s.x, player.getY(), s.z) > 6000.0 * 6000.0) continue;
            views.add(new com.agustin.bloodmoon.network.SettlementMapPayload.View(s.name, s.x, s.z, influence(ow, s), s.level, s.pop,
                    soldiers(ow, s), color(s), Port.has(s)));
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.agustin.bloodmoon.network.SettlementMapPayload(views));
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
        List<Settlement.Work> cur = new ArrayList<>();
        for (Settlement.Work w : s.works) if (w.state == 0) cur.add(w);
        int housing = 0;
        for (VillageLayout.Building b : bs) housing += capacity(b);
        double prod = production(bs, s.pop);
        String lv = switch (s.level) {
            case Settlement.CAPITAL -> "capital";
            case Settlement.CITY -> "ciudad";
            case Settlement.TOWN -> "pueblo";
            default -> "aldea";
        };
        int renewed = 0;
        for (VillageLayout.Building b : bs) if (city(b)) renewed++;
        return s.name + " (" + lv + (s.colony ? ", colonia" : "") + ", " + s.culture + ") en "
                + s.x + " " + s.z + "\n población " + s.pop + "/" + housing + " (" + s.materialized + " a la vista)"
                + " · comida " + (int) s.food + " (" + String.format(java.util.Locale.ROOT, "%+.1f", prod - s.pop) + "/día)"
                + " · tesoro " + coins(s.treasury)
                + "\n edificios " + bs.size() + " · obras: " + (cur.isEmpty() ? "ninguna" : cur.stream().map(w -> w.b.template().substring(w.b.template().indexOf('/') + 1)
                        + " " + (int) (w.progress * 100) + "%").collect(java.util.stream.Collectors.joining(", ")))
                + " · pendientes de colocar " + s.works.stream().filter(w -> w.state == 1).count()
                + (Port.has(s) ? "\n puerto: " + s.portPiers + "/" + Port.maxPiers(s) + " muelles (agua " + s.portSize + " celdas)"
                        + (s.portLighthouse ? " · faro" : "") : "")
                + "\n mercado:" + Market.report(s) + "\n tratos con jugadores " + s.playerTrades
                + (s.level >= Settlement.CITY ? "\n piedra: " + renewed + " edificios · muralla " + (s.wall.isEmpty() ? "sin empezar" : (int) (s.wallProgress * 100) + "%") : "");
    }
}

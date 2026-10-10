package com.agustin.bloodmoon;

import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.world.ColiseumSites;
import com.agustin.bloodmoon.world.VoidPortals;
import com.agustin.bloodmoon.world.design.ColiseumDesign;
import com.agustin.bloodmoon.world.design.LabyrinthDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Prueba de humo para CI (solo corre con la variable de entorno BLOODMOON_SMOKETEST=1): genera chunks del
 * Laberinto y del Coliseo, enciende el portal del altar, viaja y apaga el servidor. Escribe líneas SMOKETEST.
 */
public final class SmokeTest {
    private SmokeTest() {}

    public static boolean enabled() {
        return "1".equals(System.getenv("BLOODMOON_SMOKETEST"));
    }

    public static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) return;
        MinecraftServer server = event.getServer();
        boolean ok = true;
        try {
            ok &= labyrinth(server);
            ok &= coliseum(server);
            ok &= sanctum(server);
            ok &= beyond(server);
            ok &= dominion(server);
            ok &= humans(server);
            ok &= village(server);
            ok &= settlement(server);
        } catch (Throwable t) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL exception", t);
            ok = false;
        }
        BloodMoonMod.LOGGER.info(ok ? "SMOKETEST PASS" : "SMOKETEST FAIL");
        server.halt(false);
    }

    /** Fase 2: un asentamiento real crece 30 días como datos, coloca sus obras y quizá funda una colonia. */
    private static boolean settlement(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var site = com.agustin.bloodmoon.human.VillageSites.nearest(level, BlockPos.ZERO, 12);
        if (site.isEmpty()) return false;
        var data = com.agustin.bloodmoon.human.HumanityManager.Data.get(level);
        var s = com.agustin.bloodmoon.human.HumanityManager.register(level, data, site.get());
        int pop0 = s.pop, h0 = com.agustin.bloodmoon.human.HumanityManager.housing(level, s);
        long t0 = System.nanoTime();
        int cycles = 30 * com.agustin.bloodmoon.human.HumanityManager.CYCLES_PER_DAY;
        double minFood = Double.MAX_VALUE;
        for (int i = 0; i < cycles; i++) {
            com.agustin.bloodmoon.human.HumanityManager.cycle(level, data, s);
            minFood = Math.min(minFood, s.food);
        }
        double msPerCycle = (System.nanoTime() - t0) / 1e6 / cycles;
        BloodMoonMod.LOGGER.info("SMOKETEST humanity cycles done in {} ms", (System.nanoTime() - t0) / 1_000_000);
        int works = s.works.size();
        long t1 = System.nanoTime();
        int placed = com.agustin.bloodmoon.human.HumanityManager.placeAll(level, data, s);
        long placeMs = (System.nanoTime() - t1) / 1_000_000;
        BloodMoonMod.LOGGER.info("SMOKETEST humanity placed {} in {} ms", placed, placeMs);
        // los bloques de la primera obra en pie
        int total = 0, match = 0;
        for (var w : s.works) {
            if (w.state != 2) continue;
            var vt = com.agustin.bloodmoon.human.VillageLayout.template(level, w.b.template());
            for (var e : vt.t().blocks()) {
                if (e.state().isAir()) continue;
                int[] r = com.agustin.bloodmoon.invasion.DominionTemplates.rotate(e.x(), e.z(), w.b.rot());
                total++;
                if (level.getBlockState(new BlockPos(w.b.x() + r[0], w.b.floorY() + e.y(), w.b.z() + r[1])).getBlock() == e.state().getBlock()) match++;
            }
            break;
        }
        try {
            topDown(level, s.x, s.z, 128, "village_grown_30d");
        } catch (Exception e) {
            BloodMoonMod.LOGGER.warn("SMOKETEST grown map failed", e);
        }
        // economía: precios del pueblo, un trato con un humano de ahí y el efecto en stock y tesoro
        {
            var h = com.agustin.bloodmoon.entity.ModEntities.HUMAN.get().create(level);
            if (h != null) {
                h.moveTo(s.x + 0.5, s.y + 1, s.z + 0.5, 0, 0);
                h.setup(4242, com.agustin.bloodmoon.human.HumanJob.FARMER, s.culture);
                h.setSettlement(s.key);
                var offers = h.getOffers();
                int[] base = new int[offers.size()];
                for (int i = 0; i < base.length; i++) base[i] = com.agustin.bloodmoon.human.Market.coinSide(offers.get(i));
                com.agustin.bloodmoon.human.Market.reprice(h, s, base);
                double t0r = s.treasury;
                var o = offers.get(0);
                com.agustin.bloodmoon.human.Market.onTrade(s, o);
                BloodMoonMod.LOGGER.info("SMOKETEST humanity market {} | trade {} -> {} (coins {}) treasury {} -> {}",
                        com.agustin.bloodmoon.human.Market.report(s), o.getItemCostA().itemStack(), o.getResult(),
                        com.agustin.bloodmoon.human.Market.coinSide(o), (int) t0r, (int) s.treasury);
                h.discard();
            }
        }
        // hasta ciudad: renovación del centro, empedrado y muralla
        int day = 30;
        long tc = System.nanoTime();
        while (day < 200 && s.level < com.agustin.bloodmoon.human.Settlement.CITY) {
            for (int i = 0; i < com.agustin.bloodmoon.human.HumanityManager.CYCLES_PER_DAY; i++) com.agustin.bloodmoon.human.HumanityManager.cycle(level, data, s);
            day++;
            if (day % 10 == 0) com.agustin.bloodmoon.human.HumanityManager.placeAll(level, data, s);
        }
        int cityDay = day;
        s.treasury += 20000;   // la prueba no espera a juntar fondos para ver la renovación entera
        for (int d = 0; d < 40; d++) {
            for (int i = 0; i < com.agustin.bloodmoon.human.HumanityManager.CYCLES_PER_DAY; i++) com.agustin.bloodmoon.human.HumanityManager.cycle(level, data, s);
            if (d % 5 == 0) com.agustin.bloodmoon.human.HumanityManager.placeAll(level, data, s);
        }
        int cityPlaced = com.agustin.bloodmoon.human.HumanityManager.placeAll(level, data, s);
        int[] fin = com.agustin.bloodmoon.human.HumanityManager.finishCity(level, data, s);
        int piers = com.agustin.bloodmoon.human.Port.finish(level, data, s);
        int deck = 0;
        if (com.agustin.bloodmoon.human.Port.has(s)) {
            for (int d = 0; d < 6; d++) {
                BlockPos dp = new BlockPos(s.portX + s.portDX * (d + 2), s.portY + 1, s.portZ + s.portDZ * (d + 2));
                for (int dy = -2; dy <= 2; dy++) {
                    Block bl = level.getBlockState(dp.above(dy)).getBlock();
                    if (bl == net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS || bl == net.minecraft.world.level.block.Blocks.STONE_BRICKS
                            || bl == net.minecraft.world.level.block.Blocks.ACACIA_PLANKS) {
                        deck++;
                        break;
                    }
                }
            }
        }
        BloodMoonMod.LOGGER.info("SMOKETEST humanity port {} water={} piers={}/{} built={} deck={}/6 lighthouse={} at {} {} dir {} {}",
                s.name, s.portSize, s.portPiers, com.agustin.bloodmoon.human.Port.maxPiers(s), piers, deck, s.portLighthouse,
                s.portX, s.portZ, s.portDX, s.portDZ);
        int stoneBuildings = 0;
        for (var b : com.agustin.bloodmoon.human.HumanityManager.built(level, s)) if (b.template().contains("/city_") || b.template().endsWith("/hall") || b.template().endsWith("/market")) stoneBuildings++;
        BloodMoonMod.LOGGER.info("SMOKETEST humanity city {} level={} day={} pop={} stone={} streets={} wall={}/{} works={} placed={} in {} ms",
                s.name, s.level, cityDay, s.pop, stoneBuildings, fin[0], fin[1], s.wall.size(), s.works.size(), cityPlaced,
                (System.nanoTime() - tc) / 1_000_000);
        try {
            topDown(level, s.x, s.z, 170, "city_" + s.name.toLowerCase().replace(' ', '_'));
        } catch (Exception e) {
            BloodMoonMod.LOGGER.warn("SMOKETEST city map failed", e);
        }
        var colony = s.level >= com.agustin.bloodmoon.human.Settlement.TOWN
                ? com.agustin.bloodmoon.human.HumanityManager.foundColony(level, data, s) : null;
        BloodMoonMod.LOGGER.info("SMOKETEST humanity {} pop {}->{} housing {}->{} level={} food(min)={} treasury={} works={} placed={} blocks {}/{} cycle={}ms place={}ms colony={}",
                s.name, pop0, s.pop, h0, com.agustin.bloodmoon.human.HumanityManager.housing(level, s), s.level, (int) minFood,
                (int) s.treasury, works, placed, match, total, String.format(java.util.Locale.ROOT, "%.2f", msPerCycle), placeMs,
                colony == null ? "none" : colony.name + "@" + colony.x + "," + colony.z);
        return s.pop > pop0 && s.pop < 400 && works > 3 && placed > 0 && total > 0 && match >= total * 9 / 10 && msPerCycle < 5;
    }

    /** Aldea humana: se ubica, se generan sus chunks y los edificios quedan en pie (bloques de la plantilla en su lugar). */
    private static boolean village(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var site = com.agustin.bloodmoon.human.VillageSites.nearest(level, BlockPos.ZERO, 12);
        if (site.isEmpty()) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL no human village within 12 regions");
            return false;
        }
        boolean ok = village(level, site.get());
        // también una del desierto (la cultura que falte)
        outer:
        for (int ring = 0; ring <= 20; ring++) {
            for (int rx = -ring; rx <= ring; rx++) {
                for (int rz = -ring; rz <= ring; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) continue;
                    var d = com.agustin.bloodmoon.human.VillageSites.site(level, rx, rz);
                    if (d.isPresent() && d.get().culture() != site.get().culture()) {
                        ok &= village(level, d.get());
                        break outer;
                    }
                }
            }
        }
        return ok;
    }

    private static boolean village(ServerLevel level, com.agustin.bloodmoon.human.VillageSites.Site s) {
        var lay = com.agustin.bloodmoon.human.VillageLayout.get(level, s);
        long t0 = System.nanoTime();
        int r = com.agustin.bloodmoon.human.VillageLayout.RADIUS / 16 + 1;
        int cx = s.x() >> 4, cz = s.z() >> 4;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) level.getChunk(cx + dx, cz + dz);
        int total = 0, match = 0;
        for (var b : lay.buildings()) {
            var vt = com.agustin.bloodmoon.human.VillageLayout.template(level, b.template());
            for (var e : vt.t().blocks()) {
                if (e.state().isAir()) continue;
                int[] w = com.agustin.bloodmoon.invasion.DominionTemplates.rotate(e.x(), e.z(), b.rot());
                BlockPos p = new BlockPos(b.x() + w[0], b.floorY() + e.y(), b.z() + w[1]);
                total++;
                if (level.getBlockState(p).getBlock() == e.state().getBlock()) match++;
            }
        }
        int paths = 0;
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                int x = s.x() + dx, z = s.z() + dz;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                Block bl = level.getBlockState(new BlockPos(x, y, z)).getBlock();
                if (bl == net.minecraft.world.level.block.Blocks.DIRT_PATH || bl == net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE) paths++;
            }
        }
        try {
            topDown(level, s.x(), s.z(), 88, "village_" + s.culture().name().toLowerCase());
        } catch (Exception e) {
            BloodMoonMod.LOGGER.warn("SMOKETEST village map failed", e);
        }
        double rate = total == 0 ? 0 : (double) match / total;
        BloodMoonMod.LOGGER.info("SMOKETEST village {} at {} {} {} buildings={} [{}] blocks {}/{} ({}%) paths={} in {} ms", s.culture(), s.x(), s.y(), s.z(),
                lay.buildings().size(), lay.stats(), match, total, (int) (rate * 100), paths, (System.nanoTime() - t0) / 1_000_000);
        return lay.buildings().size() >= 10 && rate > 0.9 && paths > 30;
    }

    /** Vista cenital (colores de mapa con sombreado por altura) para revisar a ojo lo generado: run/smoke/<name>.png. */
    private static void topDown(ServerLevel level, int cx, int cz, int r, String name) throws java.io.IOException {
        int n = 2 * r + 1, sc = 4;
        var img = new java.awt.image.BufferedImage(n * sc, n * sc, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] prev = new int[n];
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int x = cx - r + dx, z = cz - r + dz;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos p = new BlockPos(x, y, z);
                BlockState st = level.getBlockState(p);
                int col = st.getMapColor(level, p).col;
                if (st.is(net.minecraft.world.level.block.Blocks.LANTERN)) col = 0xFFD040;
                double shade = dz == 0 ? 1.0 : y > prev[dx] ? 1.15 : y < prev[dx] ? 0.8 : 1.0;
                prev[dx] = y;
                int rr = Math.min(255, (int) (((col >> 16) & 255) * shade)), gg = Math.min(255, (int) (((col >> 8) & 255) * shade)),
                        bb = Math.min(255, (int) ((col & 255) * shade));
                int rgb = rr << 16 | gg << 8 | bb;
                for (int i = 0; i < sc; i++) for (int j = 0; j < sc; j++) img.setRGB(dx * sc + i, dz * sc + j, rgb);
            }
        }
        java.io.File dir = new java.io.File("smoke");
        dir.mkdirs();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, name + ".png"));
    }

    /** Humanos: cada oficio que comercia tiene ofertas en monedas (ninguna esmeralda) y un aldeano se convierte. */
    private static boolean humans(MinecraftServer server) {
        ServerLevel level = server.overworld();
        boolean ok = true;
        for (com.agustin.bloodmoon.human.HumanJob job : com.agustin.bloodmoon.human.HumanJob.values()) {
            var h = com.agustin.bloodmoon.entity.ModEntities.HUMAN.get().create(level);
            if (h == null) return false;
            h.moveTo(0.5, 100, 0.5, 0, 0);
            h.setup(job.ordinal() * 7919, job, com.agustin.bloodmoon.human.Culture.byId(job.ordinal() % 2));
            if (!job.trades()) continue;
            int n = 0, emeralds = 0;
            for (var o : h.getOffers()) {
                n++;
                if (o.getResult().is(net.minecraft.world.item.Items.EMERALD)
                        || o.getItemCostA().item().value() == net.minecraft.world.item.Items.EMERALD
                        || o.getItemCostB().map(c -> c.item().value() == net.minecraft.world.item.Items.EMERALD).orElse(false)) {
                    emeralds++;
                }
            }
            BloodMoonMod.LOGGER.info("SMOKETEST human {} offers={} emeralds={} name={}", job, n, emeralds, h.describe());
            if (n == 0 || emeralds > 0 && job != com.agustin.bloodmoon.human.HumanJob.MERCHANT) ok = false;
        }
        var v = EntityType.VILLAGER.create(level);
        if (v != null) {
            v.moveTo(0.5, 100, 0.5, 0, 0);
            v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN).setLevel(3));
            var h = com.agustin.bloodmoon.human.HumanWorld.convert(level, v);
            boolean conv = h != null && h.job() == com.agustin.bloodmoon.human.HumanJob.LIBRARIAN && h.getOffers().size() >= 4;
            BloodMoonMod.LOGGER.info("SMOKETEST human conversion {}", conv ? h.describe() : "FAILED");
            if (h != null) h.discard();
            ok &= conv;
        }
        return ok;
    }

    /** Plantillas del Dominio: se leen sin estados inválidos y una Fortaleza se levanta entera con su núcleo. */
    private static boolean dominion(MinecraftServer server) {
        ServerLevel level = server.overworld();
        boolean ok = true;
        for (String n : new String[]{"obelisk", "nest", "tower", "fortress", "gate", "soul_site", "soul_core", "spire"}) {
            var t = com.agustin.bloodmoon.invasion.DominionTemplates.get(level, n);
            BloodMoonMod.LOGGER.info("SMOKETEST dominion template {} blocks={}", n, t.blocks().size());
            if (t.blocks().isEmpty()) ok = false;
        }
        int bad = com.agustin.bloodmoon.invasion.DominionTemplates.badStates();
        if (bad > 0) { BloodMoonMod.LOGGER.error("SMOKETEST FAIL dominion bad states {}", bad); ok = false; }
        long t0 = System.nanoTime();
        int cx = 2000, cz = 2000;
        for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) level.getChunk((cx >> 4) + ox, (cz >> 4) + oz);
        var plan = com.agustin.bloodmoon.invasion.DominionTemplates.plan(level,
                com.agustin.bloodmoon.invasion.DominionTemplates.get(level, "fortress"), cx, cz, net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        for (var p : plan.placements()) level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        BlockState core = level.getBlockState(new BlockPos(cx, plan.coreY(), cz));
        BloodMoonMod.LOGGER.info("SMOKETEST dominion fortress placements={} in {} ms core={}", plan.placements().size(),
                (System.nanoTime() - t0) / 1_000_000, core);
        if (!core.is(ModBlocks.OBELISK_CORE.get())) { BloodMoonMod.LOGGER.error("SMOKETEST FAIL dominion core missing"); ok = false; }
        return ok;
    }

    private static boolean labyrinth(MinecraftServer server) {
        ServerLevel lab = server.getLevel(ModDimensions.VOID_LABYRINTH);
        if (lab == null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL labyrinth dimension missing");
            return false;
        }
        long t0 = System.nanoTime();
        Map<String, Integer> counts = new HashMap<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                lab.getChunk(cx, cz);
                for (int y = LabyrinthDesign.FLOOR - 1; y < LabyrinthDesign.FLOOR + 40; y += 3) {
                    BlockState s = lab.getBlockState(new BlockPos(cx * 16 + 5, y, cz * 16 + 9));
                    counts.merge(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(), 1, Integer::sum);
                }
            }
        }
        BloodMoonMod.LOGGER.info("SMOKETEST labyrinth 49 chunks in {} ms, blocks {}", (System.nanoTime() - t0) / 1_000_000, counts);
        boolean ok = counts.keySet().stream().anyMatch(k -> k.contains("deepslate")) && counts.size() > 2;
        if (!ok) BloodMoonMod.LOGGER.error("SMOKETEST FAIL labyrinth looks empty");
        return ok;
    }

    /** Santuario del Ojo: la arena se genera y el Observador puede despertar y tickear. */
    private static boolean sanctum(MinecraftServer server) {
        ServerLevel lab = server.getLevel(ModDimensions.VOID_LABYRINTH);
        if (lab == null) return false;
        BlockPos c = com.agustin.bloodmoon.world.EyeSanctums.nearest(lab, BlockPos.ZERO);
        if (c == null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL no sanctum");
            return false;
        }
        long t0 = System.nanoTime();
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) lab.getChunk((c.getX() >> 4) + cx, (c.getZ() >> 4) + cz);
        BloodMoonMod.LOGGER.info("SMOKETEST sanctum at {} chunks in {} ms", c, (System.nanoTime() - t0) / 1_000_000);
        int F = LabyrinthDesign.FLOOR;
        BlockState floor = lab.getBlockState(new BlockPos(c.getX() + 20, F - 1, c.getZ() + 3));
        BlockState seal = lab.getBlockState(new BlockPos(c.getX(), F + 1, c.getZ()));
        BlockState abyss = lab.getBlockState(new BlockPos(c.getX() + 60, F - 1, c.getZ() + 20));
        int pillars = 0;
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            BlockPos p = new BlockPos((int) Math.floor(c.getX() + Math.cos(a) * 30), F + 3, (int) Math.floor(c.getZ() + Math.sin(a) * 30));
            if (!lab.getBlockState(p).isAir()) pillars++;
        }
        BloodMoonMod.LOGGER.info("SMOKETEST sanctum floor={} seal={} abyss={} pillars={}", floor, seal, abyss, pillars);
        if (floor.isAir() || !seal.is(ModBlocks.VOID_BLOCK.get()) || !abyss.isAir() || pillars < 5) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL sanctum layout");
            return false;
        }
        var home = net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(c.getX(), F, c.getZ()));
        var eye = com.agustin.bloodmoon.world.EyeSanctums.spawn(lab, home);
        if (eye == null || !eye.isAlive()) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL eye spawn");
            return false;
        }
        for (int i = 0; i < 5; i++) eye.tick();
        var tentacle = com.agustin.bloodmoon.entity.ModEntities.EYE_TENTACLE.get().create(lab);
        tentacle.moveTo(home.x + 20, F, home.z);
        lab.addFreshEntity(tentacle);
        for (int i = 0; i < 30; i++) tentacle.tick();
        BloodMoonMod.LOGGER.info("SMOKETEST eye state={} y={} tentacle ext={}", eye.getState(), eye.getY(), tentacle.extension(0F));
        eye.discard();
        tentacle.discard();
        return true;
    }

    /** Más Allá: la llanura se genera, el Desatado y el Ojo Colosal tickean y el agujero llega al vacío. */
    private static boolean beyond(MinecraftServer server) {
        ServerLevel b = server.getLevel(ModDimensions.BEYOND);
        if (b == null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL beyond dimension missing");
            return false;
        }
        int[] c = com.agustin.bloodmoon.world.design.BeyondDesign.fightCenter(0);
        int F = com.agustin.bloodmoon.world.design.BeyondDesign.FLOOR;
        long t0 = System.nanoTime();
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) b.getChunk((c[0] >> 4) + cx, (c[1] >> 4) + cz);
        BlockState floor = b.getBlockState(new BlockPos(c[0] + 10, F - 1, c[1] + 3));
        BlockState sigil = b.getBlockState(new BlockPos(c[0], F - 1, c[1]));
        BloodMoonMod.LOGGER.info("SMOKETEST beyond chunks in {} ms floor={} sigil={}", (System.nanoTime() - t0) / 1_000_000, floor, sigil);
        if (floor.isAir() || !sigil.is(ModBlocks.VOID_BLOCK.get())) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL beyond layout");
            return false;
        }
        var home = new net.minecraft.world.phys.Vec3(c[0] + 0.5, F, c[1] + 0.5);
        var boss = com.agustin.bloodmoon.entity.ModEntities.UNBOUND_OBSERVER.get().create(b);
        boss.moveTo(home.x, home.y, home.z, 0F, 0F);
        boss.setHome(home);
        b.addFreshEntity(boss);
        for (int i = 0; i < 5; i++) boss.tick();
        // agujero del Ojo Colosal
        BlockPos hole = new BlockPos(c[0], F - 1, c[1] + 25);
        com.agustin.bloodmoon.world.BeyondHoles.start(b, hole, 20F);
        for (int i = 0; i < 40; i++) com.agustin.bloodmoon.world.BeyondHoles.tick(b);
        boolean through = b.getBlockState(hole.atY(5)).isAir() && b.getBlockState(hole.atY(F - 1)).isAir()
                && b.getBlockState(hole.offset(14, 0, 0)).isAir() && !b.getBlockState(hole.offset(30, 0, 0)).isAir();
        BloodMoonMod.LOGGER.info("SMOKETEST beyond boss state={} hole through={}", boss.getState(), through);
        boss.discard();
        return through;
    }

    private static boolean coliseum(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        var site = ColiseumSites.nearest(ow, BlockPos.ZERO, 8);
        if (site.isEmpty()) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL no coliseum within 8 regions");
            return false;
        }
        ColiseumSites.Site s = site.get();
        BloodMoonMod.LOGGER.info("SMOKETEST coliseum at {} {} {}", s.x(), s.y(), s.z());
        long t0 = System.nanoTime();
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) ow.getChunk((s.x() >> 4) + cx, (s.z() >> 4) + cz);
        // fachada
        ow.getChunk((s.x() + 240) >> 4, s.z() >> 4);
        BloodMoonMod.LOGGER.info("SMOKETEST coliseum chunks in {} ms", (System.nanoTime() - t0) / 1_000_000);
        int top = s.y() + ColiseumDesign.ALTAR_TOP;
        BlockState frame = ow.getBlockState(new BlockPos(s.x() + 2, top + 2, s.z()));
        BlockState facade = ow.getBlockState(new BlockPos(s.x() + 240, s.y() + 3, s.z() + 1));
        BloodMoonMod.LOGGER.info("SMOKETEST altar frame block = {}, facade block = {}", frame, facade);
        if (!frame.is(ModBlocks.VOID_BLOCK.get())) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL altar frame missing");
            return false;
        }
        if (VoidPortals.findShape(ow, new BlockPos(s.x(), top + 1, s.z())) != null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL altar portal should be incomplete");
            return false;
        }
        // completar el marco como lo haría el jugador
        BlockState vb = ModBlocks.VOID_BLOCK.get().defaultBlockState();
        for (BlockPos p : new BlockPos[]{new BlockPos(s.x() + 2, top + 4, s.z()), new BlockPos(s.x() + 2, top + 5, s.z()),
                new BlockPos(s.x() + 2, top + 6, s.z()), new BlockPos(s.x(), top + 6, s.z()), new BlockPos(s.x() + 1, top + 6, s.z())}) {
            ow.setBlock(p, vb, Block.UPDATE_ALL);
        }
        // la escalinata llega del suelo a la cima: escalón a escalón sobre el eje +x
        for (int t = 49; t >= 10; t--) {
            int expect = Math.min(ColiseumDesign.ALTAR_TOP, ColiseumDesign.STAIR_FOOT - t) - 1;
            BlockState st = ow.getBlockState(new BlockPos(s.x() + t, s.y() + expect, s.z()));
            BlockState above = ow.getBlockState(new BlockPos(s.x() + t, s.y() + expect + 1, s.z()));
            if (st.isAir() || !above.isAir()) {
                BloodMoonMod.LOGGER.error("SMOKETEST FAIL stair broken at t={} ({} / {})", t, st, above);
                return false;
            }
        }
        BloodMoonMod.LOGGER.info("SMOKETEST altar stairs continuous");
        var shape = VoidPortals.findShape(ow, new BlockPos(s.x(), top + 1, s.z()));
        if (shape == null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL altar portal shape not detected");
            return false;
        }
        BloodMoonMod.LOGGER.info("SMOKETEST portal shape {}", shape);
        BlockPos inside = new BlockPos(s.x(), top + 1, s.z());
        ow.setBlock(inside, ModBlocks.VOID_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        ArmorStand probe = new ArmorStand(EntityType.ARMOR_STAND, ow);
        probe.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        DimensionTransition dt = VoidPortals.destination(ow, probe, inside);
        if (dt == null) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL no destination");
            return false;
        }
        BlockPos arrival = BlockPos.containing(dt.pos());
        BlockState there = dt.newLevel().getBlockState(arrival);
        BloodMoonMod.LOGGER.info("SMOKETEST arrival {} in {} = {}", arrival, dt.newLevel().dimension().location(), there);
        boolean back = dt.newLevel().dimension() == ModDimensions.VOID_LABYRINTH && there.is(ModBlocks.VOID_PORTAL.get());
        if (!back) BloodMoonMod.LOGGER.error("SMOKETEST FAIL arrival portal not built");
        // vuelta
        DimensionTransition home = VoidPortals.destination(dt.newLevel(), probe, arrival);
        BloodMoonMod.LOGGER.info("SMOKETEST return {} in {}", home == null ? null : BlockPos.containing(home.pos()),
                home == null ? null : home.newLevel().dimension().location());
        return back && home != null && home.newLevel().dimension() == Level.OVERWORLD;
    }
}

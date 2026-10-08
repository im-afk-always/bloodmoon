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
        } catch (Throwable t) {
            BloodMoonMod.LOGGER.error("SMOKETEST FAIL exception", t);
            ok = false;
        }
        BloodMoonMod.LOGGER.info(ok ? "SMOKETEST PASS" : "SMOKETEST FAIL");
        server.halt(false);
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
        BlockPos hole = new BlockPos(c[0] + 60, F - 1, c[1]);
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

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

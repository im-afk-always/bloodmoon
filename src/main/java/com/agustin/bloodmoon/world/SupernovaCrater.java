package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cráter de la supernova: una esfera de radio R (el cráter queda semiesférico, de R bloques de profundidad)
 * excavada de adentro hacia afuera en capas finas, repartida en varios segundos para no congelar el servidor.
 * La onda "come" el terreno a medida que se expande. Al final recubre el fondo con Piedra del Vacío.
 * Solo excava chunks cargados (no fuerza la generación de mundo).
 */
public final class SupernovaCrater {
    /** Posiciones evaluadas por tick (la mayoría de la mitad superior es aire). */
    private static final int BUDGET = 36000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Task> TASKS = new ArrayList<>();

    private SupernovaCrater() {}

    private static final class Task {
        final ServerLevel level;
        final BlockPos center;
        final float radius;
        float r;
        boolean lined;

        Task(ServerLevel level, BlockPos center, float radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
        }
    }

    public static void start(ServerLevel level, BlockPos center, float radius) {
        if (radius < 1) return;
        TASKS.add(new Task(level, center, radius));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || TASKS.isEmpty()) return;
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.level != level) continue;
            if (step(t)) it.remove();
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }

    /** @return true cuando terminó. */
    private static boolean step(Task t) {
        if (t.r >= t.radius) {
            if (!t.lined) {
                line(t);
                t.lined = true;
            }
            return true;
        }
        float r0 = t.r;
        float dr = Mth.clamp(BUDGET / (4F * Mth.PI * Math.max(r0, 3F) * Math.max(r0, 3F)), 0.25F, 4F);
        float r1 = Math.min(t.radius, r0 + dr);
        carveShell(t, r0, r1, false);
        t.r = r1;
        return false;
    }

    private static void carveShell(Task t, float r0, float r1, boolean lining) {
        ServerLevel level = t.level;
        int cx = t.center.getX(), cy = t.center.getY(), cz = t.center.getZ();
        int R = Mth.ceil(r1);
        float r0s = r0 * r0, r1s = r1 * r1;
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState lineState = ModBlocks.VOID_STONE.get().defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int h2 = dx * dx + dz * dz;
                if (h2 >= r1s) continue;
                int x = cx + dx, z = cz + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int lo = h2 >= r0s ? 0 : Mth.ceil(Math.sqrt(r0s - h2));
                int hi = (int) Math.floor(Math.sqrt(r1s - h2 - 1e-4));
                for (int dy = lo; dy <= hi; dy++) {
                    for (int sign = -1; sign <= 1; sign += 2) {
                        if (dy == 0 && sign == 1) continue;
                        if (lining && sign == 1) continue; // el recubrimiento es solo del cuenco
                        int y = cy + sign * dy;
                        if (y < minY || y > maxY) continue;
                        int d2 = h2 + dy * dy;
                        if (d2 < r0s || d2 >= r1s) continue;
                        pos.set(x, y, z);
                        BlockState s = level.getBlockState(pos);
                        if (s.isAir() || s.getDestroySpeed(level, pos) < 0) continue;
                        if (lining) {
                            if (s.getFluidState().isEmpty() && level.random.nextFloat() < 0.7F) level.setBlock(pos, lineState, FLAGS);
                        } else {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                    }
                }
            }
        }
    }

    /** Las paredes y el fondo quedan vitrificados en Piedra del Vacío. */
    private static void line(Task t) {
        carveShell(t, t.radius, t.radius + 1.5F, true);
    }
}

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
 * El agujero del Ojo Colosal: un pozo de borde irregular que atraviesa la losa hasta el vacío, excavado de arriba
 * hacia abajo en pocas capas por tick (para no congelar el servidor). El borde queda vitrificado en Piedra del Vacío.
 */
public final class BeyondHoles {
    private static final int BUDGET = 40000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Task> TASKS = new ArrayList<>();

    private BeyondHoles() {}

    private static final class Task {
        final ServerLevel level;
        final int cx, cz, top;
        final float radius;
        final float[] edge = new float[64];
        int y;

        Task(ServerLevel level, int cx, int top, int cz, float radius) {
            this.level = level;
            this.cx = cx;
            this.cz = cz;
            this.top = top;
            this.radius = radius;
            this.y = top;
            for (int i = 0; i < edge.length; i++) edge[i] = radius * (0.88F + 0.24F * level.random.nextFloat());
            for (int pass = 0; pass < 2; pass++) {             // suavizar el borde
                float[] e = edge.clone();
                for (int i = 0; i < edge.length; i++) edge[i] = (e[(i + 63) % 64] + e[i] * 2 + e[(i + 1) % 64]) / 4F;
            }
        }

        float radiusAt(double dx, double dz, int y) {
            double a = Math.atan2(dz, dx);
            int i = Math.floorMod((int) Math.round(a / (Math.PI * 2) * 64), 64);
            float depthTaper = Math.max(0F, (y - (top - 6)) * 0.35F);   // el borde superior se abre un poco
            return edge[i] + depthTaper;
        }
    }

    /** Abre un agujero de radio r centrado en (x, z), desde topY hasta el fondo del mundo. */
    public static void start(ServerLevel level, BlockPos center, float radius) {
        TASKS.add(new Task(level, center.getX(), center.getY() + 40, center.getZ(), radius));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    /** Avanza los agujeros de este nivel (también lo usa la prueba de humo). */
    public static void tick(ServerLevel level) {
        if (TASKS.isEmpty()) return;
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

    private static boolean step(Task t) {
        ServerLevel level = t.level;
        int R = Mth.ceil(t.radius * 1.15F + 6);
        int perLayer = (2 * R + 1) * (2 * R + 1);
        int layers = Math.max(1, BUDGET / perLayer);
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rim = ModBlocks.VOID_STONE.get().defaultBlockState();
        for (int l = 0; l < layers && t.y >= minY; l++, t.y--) {
            int y = t.y;
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    int x = t.cx + dx, z = t.cz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    float r = t.radiusAt(dx, dz, y);
                    if (d > r + 1.6) continue;
                    pos.set(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir() || s.getDestroySpeed(level, pos) < 0) continue;
                    if (d <= r) level.setBlock(pos, air, FLAGS);
                    else if (level.random.nextFloat() < 0.75F) level.setBlock(pos, rim, FLAGS);   // borde vitrificado
                }
            }
        }
        return t.y < minY;
    }
}

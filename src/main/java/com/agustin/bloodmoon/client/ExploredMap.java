package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo que el jugador ya vio del Overworld, para el mapa: 4×4 muestras de color y altura por chunk (una cada 4 bloques).
 * Se toma al cargar cada chunk y se refresca cerca del jugador; se guarda en disco por mundo/servidor.
 */
public final class ExploredMap {
    private static final Map<Long, int[]> CHUNKS = new ConcurrentHashMap<>();
    private static String worldKey;
    private static int ticks;

    private ExploredMap() {}

    /** Muestra empaquetada (altura en el byte alto, color RGB abajo); 0 = no explorado. */
    public static int sample(long chunkKey, int sx, int sz) {
        int[] a = CHUNKS.get(chunkKey);
        return a == null ? 0 : a[sz * 4 + sx];
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() instanceof ClientLevel level && level.dimension() == Level.OVERWORLD) {
            sampleChunk(level, chunk);
        }
    }

    private static void sampleChunk(ClientLevel level, LevelChunk chunk) {
        int[] out = new int[16];
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        boolean any = false;
        for (int sz = 0; sz < 4; sz++) for (int sx = 0; sx < 4; sx++) {
            int lx = sx * 4 + 2, lz = sz * 4 + 2;
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            int v = 0;
            for (int k = 0; k < 4 && y >= level.getMinBuildHeight(); k++, y--) {
                BlockPos p = new BlockPos(minX + lx, y, minZ + lz);
                BlockState s = chunk.getBlockState(p);
                MapColor mc = s.getMapColor(level, p);
                if (mc == MapColor.NONE) continue;
                int h = Math.max(1, Math.min(255, (y + 64) / 2 + 1));
                v = h << 24 | (mc.col & 0xFFFFFF);
                break;
            }
            out[sz * 4 + sx] = v;
            any |= v != 0;
        }
        if (any) CHUNKS.put(chunk.getPos().toLong(), out);
    }

    /** Refresca los chunks alrededor del jugador (así el mapa ve cómo avanza el Dominio). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) return;
        if (++ticks % 200 != 0) return;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            var ch = mc.level.getChunkSource().getChunk(c.x + dx, c.z + dz, ChunkStatus.FULL, false);
            if (ch instanceof LevelChunk lc && !lc.isEmpty()) sampleChunk(mc.level, lc);
        }
    }

    // ------------------------------------------------------------------ disco

    private static String currentKey() {
        Minecraft mc = Minecraft.getInstance();
        String k;
        if (mc.getSingleplayerServer() != null) k = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else if (mc.getCurrentServer() != null) k = "mp_" + mc.getCurrentServer().ip;
        else k = "unknown";
        return k.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path file(String key) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("bloodmoon").resolve("maps").resolve(key + ".dat");
    }

    public static void load() {
        CHUNKS.clear();
        worldKey = currentKey();
        Path f = file(worldKey);
        if (!Files.exists(f)) return;
        try {
            CompoundTag t = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
            long[] keys = t.getLongArray("keys");
            int[] vals = t.getIntArray("vals");
            for (int i = 0; i < keys.length && (i + 1) * 16 <= vals.length; i++) {
                int[] a = new int[16];
                System.arraycopy(vals, i * 16, a, 0, 16);
                CHUNKS.put(keys[i], a);
            }
        } catch (Exception e) {
            BloodMoonMod.LOGGER.warn("Could not read explored map {}", f, e);
        }
    }

    public static void save() {
        if (worldKey == null || CHUNKS.isEmpty()) return;
        long[] keys = new long[CHUNKS.size()];
        int[] vals = new int[CHUNKS.size() * 16];
        int i = 0;
        for (Map.Entry<Long, int[]> e : CHUNKS.entrySet()) {
            if (i >= keys.length) break;
            keys[i] = e.getKey();
            System.arraycopy(e.getValue(), 0, vals, i * 16, 16);
            i++;
        }
        CompoundTag t = new CompoundTag();
        t.put("keys", new LongArrayTag(java.util.Arrays.copyOf(keys, i)));
        t.put("vals", new IntArrayTag(java.util.Arrays.copyOf(vals, i * 16)));
        try {
            Path f = file(worldKey);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(t, f);
        } catch (Exception e) {
            BloodMoonMod.LOGGER.warn("Could not save explored map", e);
        }
        CHUNKS.clear();
        worldKey = null;
    }
}

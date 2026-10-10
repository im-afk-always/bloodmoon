package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plantillas de las estructuras del Dominio ({@code data/bloodmoon/dominion/*.txt}), diseñadas con un generador propio
 * que ya resuelve la forma de escaleras, muros y paneles. Formato: {@code core x y z}, luego {@code P estado} por
 * entrada de la paleta y {@code x y z índice} por bloque (y = 0 es la primera capa sobre el suelo; el aire talla).
 */
public final class DominionTemplates {
    public record Entry(int x, int y, int z, BlockState state) {}

    public record Template(List<Entry> blocks, int coreX, int coreY, int coreZ, int minY, int maxY) {}

    private static final Map<String, Template> CACHE = new HashMap<>();
    private static int badStates;

    /** Estados de la paleta que no se pudieron leer (para la prueba de humo). */
    public static int badStates() {
        return badStates;
    }

    private DominionTemplates() {}

    public static void clear() {
        CACHE.clear();
    }

    public static Template get(ServerLevel level, String name) {
        return CACHE.computeIfAbsent(name, n -> load(level, n));
    }

    private static Template load(ServerLevel level, String name) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dominion/" + name + ".txt");
        List<Entry> out = new ArrayList<>();
        List<BlockState> palette = new ArrayList<>();
        int[] core = {0, 0, 0};
        int minY = 0, maxY = 0;
        Optional<net.minecraft.server.packs.resources.Resource> res = level.getServer().getResourceManager().getResource(loc);
        if (res.isEmpty()) {
            BloodMoonMod.LOGGER.error("Missing Dominion template {}", loc);
            return new Template(out, 0, 0, 0, 0, 0);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("core ")) {
                    String[] p = line.split(" ");
                    core = new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
                } else if (line.startsWith("P ")) {
                    BlockState st;
                    try {
                        st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), line.substring(2), false).blockState();
                    } catch (Exception e) {
                        BloodMoonMod.LOGGER.warn("Dominion template {}: bad state {}", name, line);
                        badStates++;
                        st = ModBlocks.BLACK_ROCK_BRICKS.get().defaultBlockState();
                    }
                    palette.add(st);
                } else {
                    String[] p = line.split(" ");
                    int y = Integer.parseInt(p[1]);
                    out.add(new Entry(Integer.parseInt(p[0]), y, Integer.parseInt(p[2]), palette.get(Integer.parseInt(p[3]))));
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        } catch (Exception e) {
            BloodMoonMod.LOGGER.error("Could not read Dominion template {}", loc, e);
        }
        return new Template(out, core[0], core[1], core[2], minY, maxY);
    }

    // ------------------------------------------------------------------ orientación

    /** La puerta de la plantilla mira a +Z: se gira para que mire hacia {@code toward}. */
    public static Rotation facing(int x, int z, int towardX, int towardZ) {
        int dx = towardX - x, dz = towardZ - z;
        if (Math.abs(dz) >= Math.abs(dx)) return dz >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
    }

    /** Gira un desplazamiento horizontal como lo hace una plantilla de estructura. */
    public static int[] rotate(int x, int z, Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    // ------------------------------------------------------------------ plano de obra

    /**
     * Arma el plano en (cx, cz): primero talla y despeja (de arriba hacia abajo), después cimienta hasta el suelo firme
     * y por último coloca la plantilla de abajo hacia arriba. La altura base es la mediana del terreno bajo la huella.
     */
    public static DominionTerraform.Plan plan(ServerLevel level, Template t, int cx, int cz, Rotation rot) {
        return planAt(level, t, cx, cz, rot, Integer.MIN_VALUE);
    }

    /** Igual que {@link #plan}, con la altura base fija (MIN_VALUE: la mediana del terreno). */
    public static DominionTerraform.Plan planAt(ServerLevel level, Template t, int cx, int cz, Rotation rot, int baseY) {
        Map<Long, int[]> columns = new HashMap<>();   // (dx,dz) girado → {minY, maxY}
        Set<Long> occupied = new HashSet<>();
        List<int[]> rotated = new ArrayList<>(t.blocks().size());
        for (Entry e : t.blocks()) {
            int[] d = rotate(e.x(), e.z(), rot);
            rotated.add(d);
            long k = col(d[0], d[1]);
            int[] mm = columns.computeIfAbsent(k, kk -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
            mm[0] = Math.min(mm[0], e.y());
            mm[1] = Math.max(mm[1], e.y());
        }
        int y0 = baseY != Integer.MIN_VALUE ? baseY : baseHeight(level, cx, cz, columns.keySet());
        for (int i = 0; i < rotated.size(); i++) {
            Entry e = t.blocks().get(i);
            occupied.add(BlockPos.asLong(cx + rotated.get(i)[0], y0 + e.y(), cz + rotated.get(i)[1]));
        }
        // la huella, un bloque más ancha, se despeja hasta 3 sobre la cima
        Set<Long> mask = new HashSet<>();
        for (long k : columns.keySet()) {
            int x = (int) (k >> 32), z = (int) k;
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) mask.add(col(x + ox, z + oz));
        }
        List<DominionTerraform.Placement> out = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rock = ModBlocks.BLACK_ROCK.get().defaultBlockState();
        for (int dy = t.maxY() + 3; dy >= 0; dy--) {
            for (long k : mask) {
                int x = cx + (int) (k >> 32), z = cz + (int) k;
                BlockPos q = new BlockPos(x, y0 + dy, z);
                if (occupied.contains(q.asLong())) continue;
                if (!level.getBlockState(q).isAir()) out.add(new DominionTerraform.Placement(q, air));
            }
        }
        // tallado (el aire de la plantilla), de arriba hacia abajo
        for (int i = rotated.size() - 1; i >= 0; i--) {
            Entry e = t.blocks().get(i);
            if (!e.state().isAir()) continue;
            BlockPos q = new BlockPos(cx + rotated.get(i)[0], y0 + e.y(), cz + rotated.get(i)[1]);
            if (!level.getBlockState(q).isAir()) out.add(new DominionTerraform.Placement(q, air));
        }
        // cimientos bajo cada columna de la plantilla
        for (Map.Entry<Long, int[]> c : columns.entrySet()) {
            int x = cx + (int) (c.getKey() >> 32), z = cz + (int) (long) c.getKey();
            int low = Math.min(0, c.getValue()[0]);
            List<DominionTerraform.Placement> fill = new ArrayList<>();
            for (int d = 1; d <= 64; d++) {   // hasta tocar suelo firme: troncos (también calcinados), hojas, plantas y agua se rellenan
                BlockPos q = new BlockPos(x, y0 + low - d, z);
                BlockState st = level.getBlockState(q);
                if (!passable(st)) break;
                fill.add(0, new DominionTerraform.Placement(q, rock));
            }
            out.addAll(fill);
        }
        for (int i = 0; i < rotated.size(); i++) {
            Entry e = t.blocks().get(i);
            if (e.state().isAir()) continue;
            out.add(new DominionTerraform.Placement(new BlockPos(cx + rotated.get(i)[0], y0 + e.y(), cz + rotated.get(i)[1]), e.state().rotate(rot)));
        }
        int[] cd = rotate(t.coreX(), t.coreZ(), rot);
        if (cd[0] != 0 || cd[1] != 0) BloodMoonMod.LOGGER.warn("Dominion template core off-center");
        return new DominionTerraform.Plan(out, y0 + t.coreY());
    }

    private static long col(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    /** Mediana de la altura del terreno bajo la huella (muestreada): ni enterrada ni colgando de un barranco. */
    /** Lo que no sostiene una estructura: aire, agua, plantas, hojas y troncos (también los calcinados de un bosque muerto). */
    private static boolean passable(BlockState st) {
        return st.isAir() || !st.getFluidState().isEmpty() || st.canBeReplaced() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS)
                || st.is(ModBlocks.CHARRED_LOG.get()) || st.is(ModBlocks.DEAD_GRASS.get());
    }

    /** Primera capa libre sobre el suelo real de la columna (sin troncos ni hojas); el agua cuenta como superficie. */
    private static int groundY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int min = level.getMinBuildHeight();
        for (int k = 0; k < 48 && y > min; k++) {
            BlockState st = level.getBlockState(new BlockPos(x, y, z));
            if (!st.getFluidState().isEmpty()) break;   // superficie del agua
            if (passable(st)) y--;
            else break;
        }
        return y + 1;
    }

    private static int baseHeight(ServerLevel level, int cx, int cz, Set<Long> cols) {
        List<Integer> hs = new ArrayList<>();
        int i = 0;
        for (long k : cols) {
            if (i++ % 7 != 0) continue;
            int x = cx + (int) (k >> 32), z = cz + (int) k;
            hs.add(groundY(level, x, z));   // el suelo de verdad: un bosque calcinado no levanta la estructura
        }
        if (hs.isEmpty()) return groundY(level, cx, cz);
        Integer[] a = hs.toArray(new Integer[0]);
        Arrays.sort(a);
        return a[a.length / 2];
    }
}

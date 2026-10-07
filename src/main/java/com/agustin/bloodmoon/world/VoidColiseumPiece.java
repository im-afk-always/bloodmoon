package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Coliseo del Vacío en ruinas (~100 bloques de diámetro), generado por código:
 *  - arena con anillos de piedra cincelada, radios de pizarra pulida y manchas de Piedra del Vacío
 *  - zigurat central de 6 niveles con un arco de obsidiana en la cima (futura entrada a la dimensión),
 *    braseros y anillos de llamas astrales; cuatro obeliscos de obsidiana alrededor
 *  - muro del podio con braseros, graderías escalonadas, cuatro portones con túnel
 *  - fachada de tres pisos con arcos, pilastras y cornisas; sectores derrumbados y bordes erosionados
 * Todo determinista a partir de la semilla de la pieza; ilumina con llamas astrales eternas.
 */
public class VoidColiseumPiece extends StructurePiece {
    static final int R_ARENA = 30, R_PODIUM = 32, R_SEAT = 46, R_OUT = 50;
    static final int TOP = 40, FACADE = 30, STOREY = 10, GATE_HALF = 3;

    private final int cx, cy, cz;
    private final long seed;

    public VoidColiseumPiece(BlockPos center, int seed) {
        super(ModStructures.VOID_COLISEUM_PIECE.get(), 0, new BoundingBox(
                center.getX() - R_OUT - 2, center.getY() - 20, center.getZ() - R_OUT - 2,
                center.getX() + R_OUT + 2, center.getY() + TOP, center.getZ() + R_OUT + 2));
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.seed = seed;
    }

    public VoidColiseumPiece(CompoundTag tag) {
        super(ModStructures.VOID_COLISEUM_PIECE.get(), tag);
        this.cx = tag.getInt("CX");
        this.cy = tag.getInt("CY");
        this.cz = tag.getInt("CZ");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
    }

    // ------------------------------------------------------------------ azar determinista

    private static long mix64(long v) {
        v ^= v >>> 33; v *= 0xff51afd7ed558ccdL;
        v ^= v >>> 33; v *= 0xc4ceb9fe1a85ec53L;
        v ^= v >>> 33;
        return v;
    }

    private double rnd(int x, int y, int z, int salt) {
        long h = mix64(seed * 0x9E3779B97F4A7C15L + x * 341873128712L + y * 132897987541L + z * 2654435761L
                + salt * 0x632BE59BD9B4E019L);
        return (h >>> 11) / (double) (1L << 53);
    }

    private double angNoise(double a) {
        double n = 0;
        double[][] fw = {{2, 0.5}, {3, 0.3}, {7, 0.2}};
        for (int k = 0; k < fw.length; k++) {
            double ph = rnd(k, 0, 0, 99) * Math.PI * 2;
            n += fw[k][1] * (0.5 + 0.5 * Math.sin(Math.toRadians(a) * fw[k][0] + ph));
        }
        return n;
    }

    private double collapseDrop(double a) {
        double n = angNoise(a);
        return n > 0.6 ? (n - 0.6) / 0.4 * 22 : 0;
    }

    // ------------------------------------------------------------------ bloques

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState fire() {
        return ModBlocks.ASTRAL_FIRE.get().defaultBlockState().setValue(AstralFireBlock.ETERNAL, true);
    }

    private BlockState pick(BlockState a, BlockState b, double chanceB, int x, int y, int z, int salt) {
        return rnd(x, y, z, salt) < chanceB ? b : a;
    }

    /** Bloque de diseño relativo al centro; h = 0 es la primera capa sobre el suelo. null = aire. */
    private BlockState design(int dx, int h, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double a = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
        boolean gate = (Math.abs(dz) <= GATE_HALF || Math.abs(dx) <= GATE_HALF) && r > R_ARENA - 1;

        // ---- arena y altar
        if (r <= R_ARENA) {
            int[][] obelisks = {{12, 12}, {-13, 12}, {12, -13}, {-13, -13}};
            for (int[] o : obelisks) {
                if (dx >= o[0] && dx <= o[0] + 1 && dz >= o[1] && dz <= o[1] + 1) {
                    if (h < 11) return h % 3 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
                    if (h == 11) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
                    if (h == 12) return fire();
                    return null;
                }
            }
            if (r <= 14.5) {
                int tier = r > 12.5 ? 1 : r > 10.5 ? 2 : r > 8.5 ? 3 : r > 6.5 ? 4 : r > 4.5 ? 5 : 6;
                if (h < tier) {
                    if (tier == 6 && h == 5 && r <= 3.5) {
                        return (dx + dz) % 2 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
                    }
                    boolean edge = r > 14.5 - 2 * (tier - 1) - 0.8;
                    if (h == tier - 1 && edge) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                    return pick(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 0.15, dx, h, dz, 12);
                }
                // arco portal en la cima (futura entrada a la dimensión del jefe)
                if (dz == 0 && (Math.abs(dx) == 3 || Math.abs(dx) == 4) && h >= 6 && h <= 13) {
                    return h < 13 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
                }
                if (dz == 0 && Math.abs(dx) <= 4 && h == 14) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
                if (dz == 0 && (dx == 0 || Math.abs(dx) == 3) && h == 15) return fire();
                // braseros de la cima
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                    if (h == 6) return Blocks.GILDED_BLACKSTONE.defaultBlockState();
                    if (h == 7) return fire();
                }
                // anillos de llamas en los escalones
                if (h == tier && (tier == 1 || tier == 3) && (a % 24) < 3 && r > 14.5 - 2 * (tier - 1) - 1.0) return fire();
                return null;
            }
            return null;
        }

        // ---- muro del podio
        if (r <= R_PODIUM) {
            if (gate) return null;
            if (h <= 4) {
                return pick(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                        Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 0.25, dx, h, dz, 0);
            }
            if (h == 5 && r > R_PODIUM - 1 && (a % 15) < 2) return fire();
            return null;
        }

        double drop = collapseDrop(a);

        // ---- graderías
        if (r <= R_SEAT) {
            int hs = 6 + (int) ((r - R_PODIUM) / 1.4);
            if (drop > 4) hs = Math.max(3, hs - (int) (drop * 0.35 * rnd(dx, 0, dz, 7) + drop * 0.2));
            if (gate) {
                if (h < 10) return null;
                return h < hs ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : null;
            }
            if (h < hs) {
                if (h == hs - 1) {
                    return pick(Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
                            0.2, dx, h, dz, 3);
                }
                return pick(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                        0.18, dx, h, dz, 4);
            }
            if (h == hs && r > R_SEAT - 1.5 && (a % 20) < 2.5) return fire();
            return null;
        }

        // ---- fachada (las pilastras sobresalen hasta R_OUT + 1)
        if (r <= R_OUT + 1) {
            double seg = 7.5;
            double p = (a % seg) / seg;
            boolean pilaster = p < 0.22;
            if (r > R_OUT && !pilaster) return null;
            double maxh = FACADE - drop - (int) (rnd((int) (a / seg), 0, 0, 11) * 3);
            if (gate && h < 13) return null;
            if (h >= maxh) {
                if (pilaster && h == (int) Math.ceil(maxh) && maxh >= FACADE - 3 && r > R_OUT) return fire();
                return null;
            }
            if (maxh - h <= 2 && rnd(dx, h, dz, 5) < 0.45) return null;
            int storey = h / STOREY, hl = h % STOREY;
            if (hl == 0 || (storey == 2 && hl == STOREY - 1)) return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            if (pilaster) {
                return (hl == 1 || hl == STOREY - 2) ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            }
            double q = (p - 0.61) / 0.36;
            double archH = 5 + 2.8 * Math.sqrt(Math.max(0, 1 - q * q));
            if (p >= 0.25 && p <= 0.97 && hl >= 1 && hl <= archH && r > R_SEAT && r <= R_OUT) {
                if (hl >= archH - 0.8 && rnd(dx, h, dz, 6) < 0.05) return Blocks.COBWEB.defaultBlockState();
                return null;
            }
            return pick(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    0.22, dx, h, dz, 8);
        }
        return null;
    }

    private BlockState floor(int dx, int dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        double a = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
        if (r <= R_ARENA) {
            if (Math.abs(r - 18) < 0.6 || Math.abs(r - 25) < 0.6) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
            if (r > 15 && (a % 45) < 1.4) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            if (rnd(dx, -1, dz, 9) < 0.05) return ModBlocks.VOID_STONE.get().defaultBlockState();
            return pick(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 0.2, dx, -1, dz, 10);
        }
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    // ------------------------------------------------------------------ colocación

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        int minX = Math.max(boundingBox.minX(), chunkBox.minX()), maxX = Math.min(boundingBox.maxX(), chunkBox.maxX());
        int minZ = Math.max(boundingBox.minZ(), chunkBox.minZ()), maxZ = Math.min(boundingBox.maxZ(), chunkBox.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState foundation = Blocks.COBBLED_DEEPSLATE.defaultBlockState();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int dx = x - cx, dz = z - cz;
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > R_OUT + 1.5) continue;

                // cimientos: rellenar hasta el terreno para que nada flote
                for (int y = cy - 2; y >= cy - 20; y--) {
                    pos.set(x, y, z);
                    BlockState s = level.getBlockState(pos);
                    if (!s.isAir() && s.getFluidState().isEmpty()) break;
                    level.setBlock(pos, foundation, 2);
                }
                pos.set(x, cy - 1, z);
                level.setBlock(pos, floor(dx, dz), 2);

                // estructura y despeje del terreno que sobre
                for (int h = 0; h < TOP; h++) {
                    pos.set(x, cy + h, z);
                    BlockState target = design(dx, h, dz);
                    level.setBlock(pos, target == null ? AIR : target, 2);
                }
            }
        }
    }
}

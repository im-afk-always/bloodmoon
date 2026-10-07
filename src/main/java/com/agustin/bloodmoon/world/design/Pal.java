package com.agustin.bloodmoon.world.design;

/**
 * Paleta de bloques de las estructuras del Vacío como enteros (sin depender de Minecraft, para poder
 * probar el diseño fuera del juego). Código = material | orientación << 8 | mitad superior << 10.
 * Orientación de escaleras: 0 = norte (-z), 1 = este (+x), 2 = sur (+z), 3 = oeste (-x).
 */
public final class Pal {
    private Pal() {}

    public static final int AIR = 0;
    public static final int DEEPSLATE_BRICKS = 1, CRACKED_DEEPSLATE_BRICKS = 2, DEEPSLATE_TILES = 3, CRACKED_DEEPSLATE_TILES = 4,
            POLISHED_DEEPSLATE = 5, CHISELED_DEEPSLATE = 6, COBBLED_DEEPSLATE = 7,
            PB_BRICKS = 8, CRACKED_PB_BRICKS = 9, POLISHED_BLACKSTONE = 10, CHISELED_PB = 11, GILDED_BLACKSTONE = 12,
            TUFF_BRICKS = 13, CHISELED_TUFF_BRICKS = 14, POLISHED_TUFF = 15,
            OBSIDIAN = 16, CRYING_OBSIDIAN = 17, VOID_STONE = 18, VOID_BLOCK = 19, ASTRAL_FIRE = 20,
            COBWEB = 21, CHAIN = 22, AMETHYST_CLUSTER = 23, POLISHED_BASALT = 24,
            DEEPSLATE_BRICK_STAIRS = 25, PB_BRICK_STAIRS = 26, TUFF_BRICK_STAIRS = 27,
            DEEPSLATE_BRICK_SLAB = 28, PB_BRICK_WALL = 29, DEEPSLATE_TILE_STAIRS = 30, CHEST = 31,
            DEEPSLATE_BRICK_WALL = 32, BLACKSTONE = 33, SPAWNER = 34, CRYING_OBSIDIAN_DIM = 35, AMETHYST_BLOCK = 36,
            SOUL_SAND = 37, BONE_BLOCK = 38, IRON_BARS = 39, DEEPSLATE_TILE_SLAB = 40;

    /** Nombres de registro (índice = material). */
    public static final String[] NAMES = new String[64];

    static {
        String[][] n = {
                {"1", "minecraft:deepslate_bricks"}, {"2", "minecraft:cracked_deepslate_bricks"}, {"3", "minecraft:deepslate_tiles"},
                {"4", "minecraft:cracked_deepslate_tiles"}, {"5", "minecraft:polished_deepslate"}, {"6", "minecraft:chiseled_deepslate"},
                {"7", "minecraft:cobbled_deepslate"}, {"8", "minecraft:polished_blackstone_bricks"}, {"9", "minecraft:cracked_polished_blackstone_bricks"},
                {"10", "minecraft:polished_blackstone"}, {"11", "minecraft:chiseled_polished_blackstone"}, {"12", "minecraft:gilded_blackstone"},
                {"13", "minecraft:tuff_bricks"}, {"14", "minecraft:chiseled_tuff_bricks"}, {"15", "minecraft:polished_tuff"},
                {"16", "minecraft:obsidian"}, {"17", "minecraft:crying_obsidian"}, {"18", "bloodmoon:void_stone"}, {"19", "bloodmoon:void_block"},
                {"20", "bloodmoon:astral_fire"}, {"21", "minecraft:cobweb"}, {"22", "minecraft:chain"}, {"23", "minecraft:amethyst_cluster"},
                {"24", "minecraft:polished_basalt"}, {"25", "minecraft:deepslate_brick_stairs"}, {"26", "minecraft:polished_blackstone_brick_stairs"},
                {"27", "minecraft:tuff_brick_stairs"}, {"28", "minecraft:deepslate_brick_slab"}, {"29", "minecraft:polished_blackstone_brick_wall"},
                {"30", "minecraft:deepslate_tile_stairs"}, {"31", "minecraft:chest"}, {"32", "minecraft:deepslate_brick_wall"},
                {"33", "minecraft:blackstone"}, {"34", "minecraft:spawner"}, {"35", "minecraft:crying_obsidian"}, {"36", "minecraft:amethyst_block"},
                {"37", "minecraft:soul_sand"}, {"38", "minecraft:bone_block"}, {"39", "minecraft:iron_bars"}, {"40", "minecraft:deepslate_tile_slab"},
        };
        for (String[] e : n) NAMES[Integer.parseInt(e[0])] = e[1];
    }

    public static int mat(int code) {
        return code & 0xFF;
    }

    public static int facing(int code) {
        return (code >> 8) & 3;
    }

    public static boolean top(int code) {
        return (code & 0x400) != 0;
    }

    public static int stairs(int mat, int facing, boolean top) {
        return mat | (facing & 3) << 8 | (top ? 0x400 : 0);
    }

    /** Dirección cardinal más cercana a (x, z). */
    public static int dir(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? 1 : 3;
        return z > 0 ? 2 : 0;
    }

    public static int opposite(int d) {
        return (d + 2) & 3;
    }
}

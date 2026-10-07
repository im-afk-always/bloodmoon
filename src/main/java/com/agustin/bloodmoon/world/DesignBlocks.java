package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.world.design.Pal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootTable;

/** Traduce los códigos de {@link Pal} a bloques reales y coloca cofres con botín y generadores. */
public final class DesignBlocks {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static volatile BlockState[] BASE;

    public static final ResourceKey<LootTable> COLISEUM_LOOT = loot("chests/void_coliseum");
    public static final ResourceKey<LootTable> LABYRINTH_LOOT = loot("chests/void_labyrinth");

    private DesignBlocks() {}

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, path));
    }

    private static BlockState[] base() {
        BlockState[] b = BASE;
        if (b != null) return b;
        b = new BlockState[Pal.NAMES.length];
        for (int i = 0; i < b.length; i++) {
            String name = Pal.NAMES[i];
            Block block = name == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(ResourceLocation.parse(name));
            b[i] = block.defaultBlockState();
        }
        if (b[Pal.ASTRAL_FIRE].hasProperty(AstralFireBlock.ETERNAL)) b[Pal.ASTRAL_FIRE] = b[Pal.ASTRAL_FIRE].setValue(AstralFireBlock.ETERNAL, true);
        BASE = b;
        return b;
    }

    public static boolean special(int code) {
        int m = Pal.mat(code);
        return m == Pal.CHEST || m == Pal.SPAWNER;
    }

    public static BlockState state(int code) {
        int m = Pal.mat(code);
        BlockState s = base()[m];
        if (s.hasProperty(StairBlock.FACING) && s.hasProperty(StairBlock.HALF)) {
            s = s.setValue(StairBlock.FACING, DIRS[Pal.facing(code)]).setValue(StairBlock.HALF, Pal.top(code) ? Half.TOP : Half.BOTTOM);
        } else if (m == Pal.CHEST) {
            s = s.setValue(ChestBlock.FACING, DIRS[Pal.facing(code)]);
        }
        return s;
    }

    /** Coloca cofres y generadores (necesitan la entidad de bloque); el resto lo hace el generador directo. */
    public static void placeSpecial(WorldGenLevel level, BlockPos pos, int code, RandomSource random, ResourceKey<LootTable> loot) {
        int m = Pal.mat(code);
        if (m == Pal.CHEST) {
            level.setBlock(pos, state(code), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
        } else if (m == Pal.SPAWNER) {
            level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                spawner.setEntityId(random.nextInt(3) == 0 ? EntityType.ENDERMAN : EntityType.WITHER_SKELETON, random);
            }
        }
    }
}

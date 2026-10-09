package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.block.VoidPortalBlock;
import com.agustin.bloodmoon.block.VoidBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BloodMoonMod.MODID);

    public static final DeferredBlock<AstralFireBlock> ASTRAL_FIRE = BLOCKS.register("astral_fire",
            () -> new AstralFireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 13)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)));

    /** Piedra del Vacío: el suelo calcinado por el mazo del Ejecutor. */
    public static final DeferredBlock<Block> VOID_STONE = BLOCKS.registerSimpleBlock("void_stone",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 9.0F)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 2));

    /** Bloque del Vacío: 9 Piedras del Vacío. Con un marco de estos y un mechero se abre el portal al Laberinto. */
    public static final DeferredBlock<VoidBlock> VOID_BLOCK = BLOCKS.register("void_block",
            () -> new VoidBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(30.0F, 1200.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .emissiveRendering((state, level, pos) -> true)
                    .lightLevel(state -> 7)));

    public static final DeferredBlock<VoidPortalBlock> VOID_PORTAL = BLOCKS.register("void_portal",
            () -> new VoidPortalBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()));

    // ---------------------------------------------------------------- el Dominio (Invasión del Vacío)

    /** Tierra Muerta: el césped que el Dominio apaga. */
    public static final DeferredBlock<Block> DEAD_GRASS_BLOCK = BLOCKS.registerSimpleBlock("dead_grass_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BLACK).strength(0.6F).sound(SoundType.ROOTED_DIRT));

    /** Tierra Yerma: la tierra de abajo, gris y sin vida. */
    public static final DeferredBlock<Block> BARREN_DIRT = BLOCKS.registerSimpleBlock("barren_dirt",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).strength(0.5F).sound(SoundType.ROOTED_DIRT));

    public static final DeferredBlock<com.agustin.bloodmoon.block.DeadGrassBlock> DEAD_GRASS = BLOCKS.register("dead_grass",
            () -> new com.agustin.bloodmoon.block.DeadGrassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BLACK).replaceable().noCollission().instabreak()
                    .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> CHARRED_LOG = BLOCKS.register("charred_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(2.0F).sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> BLACK_ROCK = BLOCKS.registerSimpleBlock("black_rock",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F).sound(SoundType.BASALT));

    public static final DeferredBlock<Block> BLACK_ROCK_BRICKS = BLOCKS.registerSimpleBlock("black_rock_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F).sound(SoundType.DEEPSLATE_BRICKS));

    public static final DeferredBlock<Block> CRACKED_BLACK_ROCK_BRICKS = BLOCKS.registerSimpleBlock("cracked_black_rock_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE_BRICKS));

    /** Farol del Vacío: cristal violeta en una jaula de roca negra; ilumina los caminos y obeliscos. */
    public static final DeferredBlock<Block> VOID_LANTERN = BLOCKS.registerSimpleBlock("void_lantern",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F).sound(SoundType.GLASS).lightLevel(state -> 13)
                    .emissiveRendering((state, level, pos) -> true));

    /** Núcleo de Obelisco: el ancla del Dominio (herramienta de diamante o mejor). */
    public static final DeferredBlock<com.agustin.bloodmoon.block.ObeliskCoreBlock> OBELISK_CORE = BLOCKS.register("obelisk_core",
            () -> new com.agustin.bloodmoon.block.ObeliskCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(40.0F, 1200.0F)
                    .sound(SoundType.AMETHYST).lightLevel(state -> 12).pushReaction(PushReaction.BLOCK)
                    .emissiveRendering((state, level, pos) -> true)));

    private ModBlocks() {}
}

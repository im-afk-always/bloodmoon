package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.block.VoidPortalBlock;
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
    public static final DeferredBlock<Block> VOID_BLOCK = BLOCKS.registerSimpleBlock("void_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(30.0F, 1200.0F)
                    .sound(SoundType.DEEPSLATE_BRICKS)
                    .lightLevel(state -> 4));

    public static final DeferredBlock<VoidPortalBlock> VOID_PORTAL = BLOCKS.register("void_portal",
            () -> new VoidPortalBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable()));

    private ModBlocks() {}
}

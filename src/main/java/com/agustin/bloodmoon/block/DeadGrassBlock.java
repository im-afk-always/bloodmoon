package com.agustin.bloodmoon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hierba Muerta: matas secas negras sobre la tierra del Dominio. No crece ni se esparce. */
public class DeadGrassBlock extends BushBlock {
    public static final MapCodec<DeadGrassBlock> CODEC = simpleCodec(DeadGrassBlock::new);
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 12, 14);

    public DeadGrassBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(com.agustin.bloodmoon.registry.ModBlocks.DEAD_GRASS_BLOCK.get())
                || state.is(com.agustin.bloodmoon.registry.ModBlocks.BARREN_DIRT.get());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}

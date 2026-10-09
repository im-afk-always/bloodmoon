package com.agustin.bloodmoon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Núcleo de Obelisco: el ancla del Dominio. Al romperse (o volar), la influencia de la zona se derrumba. */
public class ObeliskCoreBlock extends Block {
    public ObeliskCoreBlock(Properties props) {
        super(props);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            com.agustin.bloodmoon.invasion.InvasionManager.onCoreRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}

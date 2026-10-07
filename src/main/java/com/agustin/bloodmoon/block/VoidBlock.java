package com.agustin.bloodmoon.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Bloque del Vacío: runas que laten con luz púrpura (textura emisiva animada) y chispas violetas. */
public class VoidBlock extends Block {
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.72F, 0.3F, 1.0F), 0.7F);

    public VoidBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction face = Direction.getRandom(random);
        BlockPos out = pos.relative(face);
        if (level.getBlockState(out).isSolidRender(level, out)) return;
        double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        level.addParticle(SPARK, x, y, z, 0, 0.02, 0);
    }
}

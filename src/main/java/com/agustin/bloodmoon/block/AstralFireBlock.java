package com.agustin.bloodmoon.block;

import com.agustin.bloodmoon.entity.VoidKnight;
import com.agustin.bloodmoon.registry.ModEffects;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Llamas astrales (púrpuras). No se propagan ni queman bloques.
 * eternal=true: permanentes (coliseo). eternal=false: se apagan solas en 10-20 s (cráteres).
 * Quien las pisa recibe Quemadura astral.
 */
public class AstralFireBlock extends BaseFireBlock {
    public static final MapCodec<AstralFireBlock> CODEC = simpleCodec(AstralFireBlock::new);
    public static final BooleanProperty ETERNAL = BooleanProperty.create("eternal");

    public AstralFireBlock(Properties properties) {
        super(properties, 2.0F);
        registerDefaultState(stateDefinition.any().setValue(ETERNAL, false));
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ETERNAL);
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // Sin la lógica de portales del fuego vanilla: estas llamas nunca encienden portales.
        if (!level.isClientSide && !state.getValue(ETERNAL)) {
            level.scheduleTick(pos, this, 200 + level.random.nextInt(200));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ETERNAL)) level.removeBlock(pos, false);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && !(entity instanceof VoidKnight)) {
            living.addEffect(new MobEffectInstance(ModEffects.ASTRAL_BURN, 100, 0));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WITCH, pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 0.6,
                    pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        }
    }
}

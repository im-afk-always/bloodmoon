package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.block.AstralFireBlock;
import com.agustin.bloodmoon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Golpe del mazo: cráter, suelo convertido en Piedra del Vacío y llamas astrales. */
public final class VoidImpact {
    private VoidImpact() {}

    public static void crater(ServerLevel level, Vec3 at, float craterRadius, float scorchRadius, boolean griefing) {
        BlockPos center = groundBelow(level, BlockPos.containing(at));
        if (griefing) {
            int cr = Mth.ceil(craterRadius);
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-cr, -cr, -cr), center.offset(cr, cr, cr))) {
                double dx = p.getX() - center.getX(), dy = (p.getY() - center.getY()) * 1.4, dz = p.getZ() - center.getZ();
                if (dx * dx + dy * dy + dz * dz <= craterRadius * craterRadius && breakable(level, p)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            scorchAround(level, center, scorchRadius);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.WITCH, at.x, at.y + 1, at.z, 120, scorchRadius * 0.6, 1.0, scorchRadius * 0.6, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 60, scorchRadius * 0.5, 1.0, scorchRadius * 0.5, 0.05);
    }

    /**
     * Cráter de impacto del bólido: cuenco semiesférico achatado (radio r, profundidad ~r/2), borde de
     * Piedra del Vacío y algunas llamas astrales. Se hace siempre (es la entrada del jefe).
     */
    public static void meteorCrater(ServerLevel level, Vec3 at, float r) {
        BlockPos center = groundBelow(level, BlockPos.containing(at));
        int R = Mth.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-R, -R, -R), center.offset(R, R + 4, R))) {
            double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ(), dy = p.getY() - center.getY();
            double d = dx * dx + dz * dz + (dy < 0 ? dy * dy * 4 : dy * dy * 0.6);
            if (d <= r * r && breakable(level, p)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        // borde y fondo vitrificados
        for (int dx = -R - 2; dx <= R + 2; dx++) {
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double hr = Math.sqrt(dx * dx + dz * dz);
                if (hr > r + 2) continue;
                BlockPos top = surface(level, center.offset(dx, 0, dz), R);
                if (top == null || !breakable(level, top)) continue;
                if (level.random.nextFloat() < 0.75F) level.setBlock(top, ModBlocks.VOID_STONE.get().defaultBlockState(), 3);
                BlockPos above = top.above();
                if (hr < r && level.getBlockState(above).isAir() && level.random.nextFloat() < 0.12F) {
                    level.setBlock(above, ModBlocks.ASTRAL_FIRE.get().defaultBlockState().setValue(AstralFireBlock.ETERNAL, false), 3);
                }
            }
        }
    }

    /** Calcina el suelo (Piedra del Vacío + llamas astrales) sin abrir cráter ni grandes partículas. */
    public static void scorch(ServerLevel level, Vec3 at, float radius, boolean griefing) {
        if (!griefing || radius <= 0) return;
        scorchAround(level, groundBelow(level, BlockPos.containing(at)), radius);
    }

    private static void scorchAround(ServerLevel level, BlockPos center, float scorchRadius) {
        int r = Mth.ceil(scorchRadius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > scorchRadius || level.random.nextFloat() < d / scorchRadius * 0.35F) continue;
                BlockPos top = surface(level, center.offset(dx, 0, dz), r + 2);
                if (top == null || !breakable(level, top)) continue;
                level.setBlock(top, ModBlocks.VOID_STONE.get().defaultBlockState(), 3);
                BlockPos above = top.above();
                if (level.getBlockState(above).isAir() && level.random.nextFloat() < 0.4F) {
                    level.setBlock(above, ModBlocks.ASTRAL_FIRE.get().defaultBlockState().setValue(AstralFireBlock.ETERNAL, false), 3);
                }
            }
        }
    }

    private static boolean breakable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getDestroySpeed(level, pos) >= 0 && !s.is(BlockTags.WITHER_IMMUNE)
                && !s.is(ModBlocks.ASTRAL_FIRE.get()) && level.getBlockEntity(pos) == null;
    }

    private static BlockPos groundBelow(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 8; i++) {
            if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return pos.below();
            pos = pos.below();
        }
        return pos;
    }

    /** Bloque sólido más alto de la columna cerca de la altura dada. */
    private static BlockPos surface(ServerLevel level, BlockPos start, int range) {
        BlockPos p = start.above(range);
        for (int i = 0; i < range * 2 + 1; i++) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.isFaceSturdy(level, p, Direction.UP) && level.getBlockState(p.above()).isAir()) return p;
            p = p.below();
        }
        return null;
    }
}

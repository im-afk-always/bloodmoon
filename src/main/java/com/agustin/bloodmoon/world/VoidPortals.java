package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.block.VoidPortalBlock;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.world.design.LabyrinthDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Portales del Vacío: marco rectangular de Bloques del Vacío (interior de 2×3 a 21×21) que se enciende
 * con mechero o carga ígnea. Conecta el mundo normal con el Laberinto del Vacío a escala 1:1;
 * si en el destino no hay un portal cerca, construye uno en una cámara segura.
 */
public final class VoidPortals {
    private static final int SEARCH = 48;

    private VoidPortals() {}

    // ------------------------------------------------------------------ encendido

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) return;
        Level level = event.getLevel();
        BlockPos clicked = event.getPos();
        if (!level.getBlockState(clicked).is(ModBlocks.VOID_BLOCK.get())) return;
        Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
        Shape shape = findShape(level, clicked.relative(face));
        if (shape == null) return;
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            shape.fill(level);
            PortalData.get(sl).add(shape.bottomLeft());
            level.playSound(null, clicked, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.5F, 1.5F);
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, event.getEntity(), LivingEntity.getSlotForHand(event.getHand()));
            } else if (!event.getEntity().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    public record Shape(BlockPos bottomLeft, Direction right, int width, int height, Direction.Axis axis) {
        void fill(Level level) {
            BlockState state = ModBlocks.VOID_PORTAL.get().defaultBlockState().setValue(VoidPortalBlock.AXIS, axis);
            for (int i = 0; i < width; i++)
                for (int j = 0; j < height; j++)
                    level.setBlock(bottomLeft.relative(right, i).above(j), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static boolean empty(BlockState s) {
        return s.isAir() || s.getBlock() instanceof BaseFireBlock;
    }

    private static boolean frame(BlockState s) {
        return s.is(ModBlocks.VOID_BLOCK.get());
    }

    public static Shape findShape(Level level, BlockPos start) {
        if (!empty(level.getBlockState(start))) return null;
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Shape s = findShape(level, start, axis);
            if (s != null) return s;
        }
        return null;
    }

    private static Shape findShape(Level level, BlockPos start, Direction.Axis axis) {
        Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction left = right.getOpposite();
        BlockPos p = start;
        for (int i = 0; i < 21 && empty(level.getBlockState(p.below())); i++) p = p.below();
        if (!frame(level.getBlockState(p.below()))) return null;
        for (int i = 0; i < 21 && empty(level.getBlockState(p.relative(left))); i++) p = p.relative(left);
        if (!frame(level.getBlockState(p.relative(left)))) return null;
        int width = 0;
        while (width < 21 && empty(level.getBlockState(p.relative(right, width)))
                && frame(level.getBlockState(p.relative(right, width).below()))) width++;
        if (width < 2 || !frame(level.getBlockState(p.relative(right, width)))) return null;
        int height = 0;
        rows:
        for (; height < 21; height++) {
            if (!frame(level.getBlockState(p.relative(left).above(height))) || !frame(level.getBlockState(p.relative(right, width).above(height)))) break;
            for (int i = 0; i < width; i++) if (!empty(level.getBlockState(p.relative(right, i).above(height)))) break rows;
        }
        if (height < 3) return null;
        for (int i = 0; i < width; i++) if (!frame(level.getBlockState(p.relative(right, i).above(height)))) return null;
        return new Shape(p.immutable(), right, width, height, axis);
    }

    // ------------------------------------------------------------------ viaje

    public static DimensionTransition destination(ServerLevel level, Entity entity, BlockPos pos) {
        ResourceKey<Level> key = level.dimension() == ModDimensions.VOID_LABYRINTH ? Level.OVERWORLD : ModDimensions.VOID_LABYRINTH;
        ServerLevel target = level.getServer().getLevel(key);
        if (target == null) return null;
        BlockPos near = target.getWorldBorder().clampToBounds(entity.getX(), pos.getY(), entity.getZ());
        BlockPos arrival = findOrCreate(target, near);
        return new DimensionTransition(target, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET));
    }

    private static BlockPos findOrCreate(ServerLevel level, BlockPos near) {
        PortalData data = PortalData.get(level);
        BlockPos best = null;
        double bestD = SEARCH * SEARCH;
        for (BlockPos p : new ArrayList<>(data.portals)) {
            double d = (p.getX() - near.getX()) * (double) (p.getX() - near.getX()) + (p.getZ() - near.getZ()) * (double) (p.getZ() - near.getZ());
            if (d > bestD) continue;
            if (!level.getBlockState(p).is(ModBlocks.VOID_PORTAL.get())) {
                data.remove(p);
                continue;
            }
            best = p;
            bestD = d;
        }
        if (best != null) return best;
        BlockPos built = build(level, near);
        data.add(built);
        return built;
    }

    /** Cámara de 6×5×6 con piso de Piedra del Vacío y un portal encendido de 2×3 (eje X). */
    private static BlockPos build(ServerLevel level, BlockPos near) {
        int x = near.getX(), z = near.getZ();
        int y;
        if (level.dimension() == ModDimensions.VOID_LABYRINTH) {
            y = LabyrinthDesign.FLOOR;
            if (level.getChunkSource().getGenerator() instanceof LabyrinthChunkGenerator gen) {
                int[] spot = gen.design(level.getChunkSource().randomState()).safeSpot(x, z);
                x = spot[0];
                z = spot[1];
            }
        } else {
            level.getChunk(x >> 4, z >> 4);
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            y = Math.max(level.getMinBuildHeight() + 6, Math.min(level.getMaxBuildHeight() - 12, Math.max(y, level.getSeaLevel() + 1)));
        }
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockState floor = ModBlocks.VOID_STONE.get().defaultBlockState();
        BlockState frame = ModBlocks.VOID_BLOCK.get().defaultBlockState();
        for (int dx = -2; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 5; dy++) level.setBlock(new BlockPos(x + dx, y + dy, z + dz), air, Block.UPDATE_ALL);
                BlockPos f = new BlockPos(x + dx, y - 1, z + dz);
                if (!level.getBlockState(f).isFaceSturdy(level, f, Direction.UP)) level.setBlock(f, floor, Block.UPDATE_ALL);
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
                if (edge) level.setBlock(new BlockPos(x + dx, y + dy, z), frame, Block.UPDATE_ALL);
            }
        }
        new Shape(new BlockPos(x, y, z), Direction.EAST, 2, 3, Direction.Axis.X).fill(level);
        return new BlockPos(x, y, z);
    }

    // ------------------------------------------------------------------ registro de portales por dimensión

    public static final class PortalData extends SavedData {
        private static final String NAME = "bloodmoon_void_portals";
        final List<BlockPos> portals = new ArrayList<>();

        public static PortalData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PortalData::new, PortalData::load, null), NAME);
        }

        private static PortalData load(CompoundTag tag, HolderLookup.Provider registries) {
            PortalData d = new PortalData();
            for (long l : tag.getLongArray("portals")) d.portals.add(BlockPos.of(l));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("portals", new LongArrayTag(portals.stream().mapToLong(BlockPos::asLong).toArray()));
            return tag;
        }

        void add(BlockPos p) {
            for (BlockPos q : portals) if (q.distManhattan(p) < 4) return;
            portals.add(p.immutable());
            setDirty();
        }

        void remove(BlockPos p) {
            portals.remove(p);
            setDirty();
        }
    }
}

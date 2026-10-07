package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.registry.ModStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/** Coliseo del Vacío: una sola pieza procedural enorme (ver VoidColiseumPiece). */
public class VoidColiseumStructure extends Structure {
    public static final MapCodec<VoidColiseumStructure> CODEC = simpleCodec(VoidColiseumStructure::new);

    public VoidColiseumStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        int y = height(context, x, z);
        if (y <= context.chunkGenerator().getSeaLevel() + 1) return Optional.empty();

        // descartar acantilados: el coliseo necesita terreno más o menos parejo
        int min = y, max = y;
        for (int[] o : new int[][]{{30, 0}, {-30, 0}, {0, 30}, {0, -30}, {21, 21}, {-21, -21}}) {
            int h = height(context, x + o[0], z + o[1]);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > 18 || min <= context.chunkGenerator().getSeaLevel()) return Optional.empty();

        BlockPos center = new BlockPos(x, y, z);
        int seed = context.random().nextInt();
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new VoidColiseumPiece(center, seed))));
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.VOID_COLISEUM.get();
    }
}

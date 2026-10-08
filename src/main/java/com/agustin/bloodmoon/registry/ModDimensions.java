package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.LabyrinthChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDimensions {
    public static final ResourceKey<Level> VOID_LABYRINTH =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_labyrinth"));

    public static final ResourceKey<Level> BEYOND =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "beyond"));

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, BloodMoonMod.MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<LabyrinthChunkGenerator>> LABYRINTH =
            CHUNK_GENERATORS.register("labyrinth", () -> LabyrinthChunkGenerator.CODEC);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<com.agustin.bloodmoon.world.BeyondChunkGenerator>> BEYOND_GEN =
            CHUNK_GENERATORS.register("beyond", () -> com.agustin.bloodmoon.world.BeyondChunkGenerator.CODEC);

    private ModDimensions() {}
}

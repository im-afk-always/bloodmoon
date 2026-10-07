package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.VoidColiseumPiece;
import com.agustin.bloodmoon.world.VoidColiseumStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, BloodMoonMod.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, BloodMoonMod.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<VoidColiseumStructure>> VOID_COLISEUM =
            STRUCTURE_TYPES.register("void_coliseum", () -> () -> VoidColiseumStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> VOID_COLISEUM_PIECE =
            PIECE_TYPES.register("void_coliseum_piece",
                    () -> (StructurePieceType.ContextlessType) VoidColiseumPiece::new);

    /** Tag usado por el Compás (data/bloodmoon/tags/worldgen/structure/void_coliseum.json). */
    public static final TagKey<Structure> VOID_COLISEUM_TAG = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_coliseum"));

    private ModStructures() {}
}

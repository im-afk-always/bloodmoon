package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.world.ColiseumFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, BloodMoonMod.MODID);

    public static final DeferredHolder<Feature<?>, ColiseumFeature> VOID_COLISEUM =
            FEATURES.register("void_coliseum", () -> new ColiseumFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, com.agustin.bloodmoon.human.VillageFeature> HUMAN_VILLAGE =
            FEATURES.register("human_village", () -> new com.agustin.bloodmoon.human.VillageFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}

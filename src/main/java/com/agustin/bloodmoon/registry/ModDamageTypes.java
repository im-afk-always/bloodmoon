package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Tipo de daño de la Quemadura astral (data/bloodmoon/damage_type/astral_burn.json). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ASTRAL_BURN = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "astral_burn"));

    private ModDamageTypes() {}

    public static DamageSource astralBurn(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ASTRAL_BURN));
    }
}

package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.effect.AstralBurnEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BloodMoonMod.MODID);

    public static final DeferredHolder<MobEffect, AstralBurnEffect> ASTRAL_BURN =
            EFFECTS.register("astral_burn", AstralBurnEffect::new);

    private ModEffects() {}
}

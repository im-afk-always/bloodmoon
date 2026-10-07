package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Solo se usa para tener los sprites de las nubes de hongo en el atlas de partículas. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, BloodMoonMod.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NUKE_PUFF =
            PARTICLES.register("nuke_puff", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SUPERNOVA_GLOW =
            PARTICLES.register("supernova_glow", () -> new SimpleParticleType(true));

    private ModParticles() {}
}

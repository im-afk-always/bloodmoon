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

    /** Rayos de estática del aura de devoción (los dibuja el cliente). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEVOTION_BOLT =
            PARTICLES.register("devotion_bolt", () -> new SimpleParticleType(true));

    /** Llamas de sangre del aura de devoción (las dibuja el cliente). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEVOTION_FLAME =
            PARTICLES.register("devotion_flame", () -> new SimpleParticleType(true));

    /** Motas de luz dorada del aura de la Providencia. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEVOTION_MOTE =
            PARTICLES.register("devotion_mote", () -> new SimpleParticleType(true));

    private ModParticles() {}
}

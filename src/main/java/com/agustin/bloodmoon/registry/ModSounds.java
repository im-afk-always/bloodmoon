package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BloodMoonMod.MODID);

    /** Estallido de la supernova del Dragón de la Primera Alma (sintetizado: golpe grave + retumbo de 9 s). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERNOVA_BLAST = register("supernova_blast");
    /** La luz colapsando en un punto, justo antes del estallido. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERNOVA_CHARGE = register("supernova_charge");

    // El Observador
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_AWAKEN = register("eye_awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_SCREAM = register("eye_scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_CHARGE = register("eye_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_BEAM = register("eye_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_WHISPER = register("eye_whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_PULSE = register("eye_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_WAVE = register("eye_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_IMPLODE = register("eye_implode");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_TENTACLE = register("eye_tentacle");

    /** Música de la batalla final: suena en el Más Allá de la Grieta en lugar de la música normal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_BEYOND = register("music_beyond");
    /** Música de la Luna de la Cosecha (Lacrimosa, Mozart). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HARVEST = register("music_harvest");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, name)));
    }

    private ModSounds() {}
}

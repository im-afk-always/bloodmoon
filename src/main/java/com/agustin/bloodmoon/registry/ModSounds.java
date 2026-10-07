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

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, name)));
    }

    private ModSounds() {}
}

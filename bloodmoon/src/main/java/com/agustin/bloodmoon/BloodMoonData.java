package com.agustin.bloodmoon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Estado persistente de la Luna de Sangre (se guarda con el mundo del Overworld).
 */
public class BloodMoonData extends SavedData {
    private static final String NAME = BloodMoonMod.MODID;

    /** true mientras la Luna de Sangre está en curso. */
    private boolean active;
    /** Índice de día cuya noche fue forzada por comando (-1 = ninguno). */
    private long forcedNightDay = -1L;

    public static BloodMoonData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BloodMoonData::new, BloodMoonData::load, null), NAME);
    }

    private static BloodMoonData load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonData data = new BloodMoonData();
        data.active = tag.getBoolean("active");
        data.forcedNightDay = tag.contains("forcedNightDay") ? tag.getLong("forcedNightDay") : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("active", active);
        tag.putLong("forcedNightDay", forcedNightDay);
        return tag;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setDirty();
    }

    public long getForcedNightDay() {
        return forcedNightDay;
    }

    public void setForcedNightDay(long day) {
        this.forcedNightDay = day;
        setDirty();
    }
}

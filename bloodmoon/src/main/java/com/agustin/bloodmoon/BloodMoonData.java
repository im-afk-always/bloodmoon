package com.agustin.bloodmoon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Estado persistente del ciclo (se guarda con el Overworld). */
public class BloodMoonData extends SavedData {
    private static final String NAME = BloodMoonMod.MODID;

    private MoonType active = MoonType.NONE;
    private MoonType forcedType = MoonType.NONE;
    /** Índice de día cuya noche fue forzada por comando (-1 = ninguno). */
    private long forcedNightDay = -1L;

    public static BloodMoonData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BloodMoonData::new, BloodMoonData::load, null), NAME);
    }

    private static BloodMoonData load(CompoundTag tag, HolderLookup.Provider registries) {
        BloodMoonData data = new BloodMoonData();
        if (tag.contains("type")) {
            data.active = MoonType.byId(tag.getInt("type"));
        } else if (tag.getBoolean("active")) { // compatibilidad con la v1.0
            data.active = MoonType.BLOOD;
        }
        data.forcedType = tag.contains("forcedType") ? MoonType.byId(tag.getInt("forcedType")) : MoonType.BLOOD;
        data.forcedNightDay = tag.contains("forcedNightDay") ? tag.getLong("forcedNightDay") : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("type", active.ordinal());
        tag.putInt("forcedType", forcedType.ordinal());
        tag.putLong("forcedNightDay", forcedNightDay);
        return tag;
    }

    public MoonType getActive() { return active; }
    public void setActive(MoonType type) { this.active = type; setDirty(); }

    public MoonType getForcedType() { return forcedType; }
    public long getForcedNightDay() { return forcedNightDay; }

    public void setForced(MoonType type, long day) {
        this.forcedType = type;
        this.forcedNightDay = day;
        setDirty();
    }

    public void clearForced() {
        this.forcedNightDay = -1L;
        setDirty();
    }
}

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
    /** Día del Eclipse Solar en curso o programado (-1 = ninguno). */
    private long eclipseDay = -1L;
    /** Eclipse congelado por /bloodmoon eclipse permanent, y el valor de doDaylightCycle a restaurar. */
    private boolean eclipsePermanent, savedDaylight = true;

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
        data.eclipseDay = tag.contains("eclipseDay") ? tag.getLong("eclipseDay") : -1L;
        data.eclipsePermanent = tag.getBoolean("eclipsePermanent");
        data.savedDaylight = !tag.contains("savedDaylight") || tag.getBoolean("savedDaylight");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("type", active.ordinal());
        tag.putInt("forcedType", forcedType.ordinal());
        tag.putLong("forcedNightDay", forcedNightDay);
        tag.putLong("eclipseDay", eclipseDay);
        tag.putBoolean("eclipsePermanent", eclipsePermanent);
        tag.putBoolean("savedDaylight", savedDaylight);
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

    public long getEclipseDay() { return eclipseDay; }
    public void setEclipseDay(long day) { this.eclipseDay = day; setDirty(); }
    public boolean isEclipsePermanent() { return eclipsePermanent; }
    public boolean getSavedDaylight() { return savedDaylight; }
    public void setEclipsePermanent(boolean on, boolean daylight) { this.eclipsePermanent = on; this.savedDaylight = daylight; setDirty(); }

    public void clearForced() {
        this.forcedNightDay = -1L;
        setDirty();
    }
}

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
    // ---- ofrenda a la deidad de la cosecha
    /** Lunas salteadas u ofrendas incompletas acumuladas (3 = la próxima luna no deja dormir). */
    private int offenses;
    private boolean offeringActive, offeringUnskippable, offeringSettled;
    private int offeringRequired, offeringDone;

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
        data.offenses = tag.getInt("offenses");
        data.offeringActive = tag.getBoolean("offeringActive");
        data.offeringUnskippable = tag.getBoolean("offeringUnskippable");
        data.offeringSettled = tag.getBoolean("offeringSettled");
        data.offeringRequired = tag.getInt("offeringRequired");
        data.offeringDone = tag.getInt("offeringDone");
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
        tag.putInt("offenses", offenses);
        tag.putBoolean("offeringActive", offeringActive);
        tag.putBoolean("offeringUnskippable", offeringUnskippable);
        tag.putBoolean("offeringSettled", offeringSettled);
        tag.putInt("offeringRequired", offeringRequired);
        tag.putInt("offeringDone", offeringDone);
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

    public int getOffenses() { return offenses; }
    public void setOffenses(int n) { offenses = Math.max(0, n); setDirty(); }
    public boolean isOfferingActive() { return offeringActive; }
    public boolean isOfferingUnskippable() { return offeringUnskippable; }
    /** true si esta luna ya se resolvió (ofrenda cumplida o castigo aplicado al saltearla). */
    public boolean isOfferingSettled() { return offeringSettled; }
    public int getOfferingRequired() { return offeringRequired; }
    public int getOfferingDone() { return offeringDone; }
    public boolean isOfferingComplete() { return offeringDone >= offeringRequired; }

    public void startOffering(int required, boolean unskippable) {
        offeringActive = true; offeringUnskippable = unskippable; offeringSettled = false;
        offeringRequired = required; offeringDone = 0; setDirty();
    }
    public void stopOffering() { offeringActive = false; offeringUnskippable = false; setDirty(); }
    public void addOfferingDone(int n) { offeringDone += n; setDirty(); }
    public void addOfferingRequired(int n) { offeringRequired += n; setDirty(); }
    public void setOfferingSettled() { offeringSettled = true; setDirty(); }
    public boolean getSavedDaylight() { return savedDaylight; }
    public void setEclipsePermanent(boolean on, boolean daylight) { this.eclipsePermanent = on; this.savedDaylight = daylight; setDirty(); }

    public void clearForced() {
        this.forcedNightDay = -1L;
        setDirty();
    }
}

package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de las invasiones (se guarda con el Overworld): las facciones y la grilla abstracta de chunks.
 * Cada chunk pertenece a lo sumo a un Dominio.
 */
public class InvasionData extends SavedData {
    private static final String NAME = BloodMoonMod.MODID + "_invasion";
    public static final int NO_CORE = Integer.MIN_VALUE;

    public final List<Faction> factions = new ArrayList<>();
    public final Map<Long, Cell> cells = new HashMap<>();
    public int nextId = 1;

    /** Un chunk del Dominio. */
    public static final class Cell {
        public int faction;
        /** 0-100 (100 = Muerto). */
        public int influence;
        /** Etapa ya aplicada a los bloques del chunk (0-3). */
        public int applied;
        /** Tiene obelisco con núcleo vivo / la estructura ya se levantó. */
        public boolean obelisk, obeliskBuilt;
        /** Altura del núcleo del obelisco construido. */
        public int coreY = NO_CORE;

        public int stage() {
            return stageOf(influence);
        }
    }

    public static int stageOf(int influence) {
        return influence <= 0 ? 0 : influence < 50 ? 1 : influence < 100 ? 2 : 3;
    }

    public static InvasionData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(InvasionData::new, InvasionData::load, null), NAME);
    }

    public Faction faction(int id) {
        for (Faction f : factions) if (f.id == id) return f;
        return null;
    }

    private static InvasionData load(CompoundTag tag, HolderLookup.Provider reg) {
        InvasionData d = new InvasionData();
        d.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag fl = tag.getList("factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < fl.size(); i++) d.factions.add(Faction.load(fl.getCompound(i), reg));
        long[] keys = tag.getLongArray("cellKeys");
        int[] vals = tag.getIntArray("cellVals");
        int[] cores = tag.getIntArray("cellCores");
        for (int i = 0; i < keys.length && i < vals.length; i++) {
            Cell c = new Cell();
            int v = vals[i];
            c.faction = v >>> 16;
            c.influence = (v >>> 8) & 0xFF;
            c.applied = (v >>> 2) & 0x7;
            c.obelisk = (v & 1) != 0;
            c.obeliskBuilt = (v & 2) != 0;
            c.coreY = i < cores.length ? cores[i] : NO_CORE;
            d.cells.put(keys[i], c);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        tag.putInt("nextId", nextId);
        ListTag fl = new ListTag();
        for (Faction f : factions) fl.add(f.save(reg));
        tag.put("factions", fl);
        long[] keys = new long[cells.size()];
        int[] vals = new int[cells.size()], cores = new int[cells.size()];
        int i = 0;
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            Cell c = e.getValue();
            keys[i] = e.getKey();
            vals[i] = (c.faction & 0xFFFF) << 16 | (c.influence & 0xFF) << 8 | (c.applied & 0x7) << 2
                    | (c.obeliskBuilt ? 2 : 0) | (c.obelisk ? 1 : 0);
            cores[i] = c.coreY;
            i++;
        }
        tag.put("cellKeys", new LongArrayTag(keys));
        tag.put("cellVals", new IntArrayTag(vals));
        tag.put("cellCores", new IntArrayTag(cores));
        return tag;
    }
}

package com.agustin.bloodmoon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Devoción de cada jugador (se guarda con el Overworld): deidad, reputación, oferta pendiente y aporte a la ofrenda en curso. */
public class DevotionData extends SavedData {
    private static final String NAME = BloodMoonMod.MODID + "_devotion";

    public static final class Entry {
        /** Deidad a la que es devoto (null = ninguna). */
        public Deity deity;
        public int reputation;
        /** Deidad que le ofreció su culto y espera respuesta (null = ninguna). */
        public Deity pendingOffer;
    }

    private final Map<UUID, Entry> players = new HashMap<>();
    /** Ofrendas aportadas por cada jugador en la luna en curso. */
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();

    public static DevotionData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DevotionData::new, DevotionData::load, null), NAME);
    }

    private static DevotionData load(CompoundTag tag, HolderLookup.Provider registries) {
        DevotionData data = new DevotionData();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("id")) continue;
            Entry e = new Entry();
            e.deity = Deity.byId(t.getString("deity"));
            e.reputation = t.getInt("reputation");
            e.pendingOffer = Deity.byId(t.getString("pending"));
            data.players.put(t.getUUID("id"), e);
        }
        ListTag contrib = tag.getList("contributions", Tag.TAG_COMPOUND);
        for (int i = 0; i < contrib.size(); i++) {
            CompoundTag t = contrib.getCompound(i);
            if (t.hasUUID("id")) data.contributions.put(t.getUUID("id"), t.getInt("n"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("deity", e.deity == null ? "" : e.deity.id());
            t.putInt("reputation", e.reputation);
            t.putString("pending", e.pendingOffer == null ? "" : e.pendingOffer.id());
            list.add(t);
        });
        tag.put("players", list);
        ListTag contrib = new ListTag();
        contributions.forEach((id, n) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putInt("n", n);
            contrib.add(t);
        });
        tag.put("contributions", contrib);
        return tag;
    }

    /** Entrada del jugador (la crea si no existe). Quien la modifique debe llamar a setDirty(). */
    public Entry entry(UUID id) {
        return players.computeIfAbsent(id, k -> new Entry());
    }

    public Map<UUID, Integer> contributions() { return contributions; }

    public void addContribution(UUID id) {
        contributions.merge(id, 1, Integer::sum);
        setDirty();
    }

    public void clearContributions() {
        contributions.clear();
        setDirty();
    }
}

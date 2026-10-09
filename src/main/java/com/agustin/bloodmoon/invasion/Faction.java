package com.agustin.bloodmoon.invasion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Un Dominio del Vacío: la facción que nace en un coliseo despierto. */
public final class Faction {
    public static final int RELIC_CAP = 2000;

    public int id;
    public String name = "";
    /** Eje del Dominio: el portal del coliseo. */
    public BlockPos center = BlockPos.ZERO;
    public long awakenedAt;
    public double essence, earned;
    public int phase;
    /** Activo: crece. Sanando: fue vencido y la tierra se cura. Ninguno de los dos: vencido y curado. */
    public boolean active = true, healing;
    public int forgers, troops;
    public final List<RankRecord> ranks = new ArrayList<>();
    /** Lo que el Dominio se tragó de cofres y barriles: vuelve al vencerlo. */
    public final List<ItemStack> relic = new ArrayList<>();
    /** Cache de chunks con agua (no se guarda). */
    final Map<Long, Boolean> water = new HashMap<>();

    public static final class RankRecord {
        public InvasionRank rank;
        public String name;
        public boolean alive = true;

        RankRecord(InvasionRank rank, String name) {
            this.rank = rank;
            this.name = name;
        }
    }

    public long aliveCount(InvasionRank rank) {
        return ranks.stream().filter(r -> r.rank == rank && r.alive).count();
    }

    CompoundTag save(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("name", name);
        t.putLong("center", center.asLong());
        t.putLong("awakened", awakenedAt);
        t.putDouble("essence", essence);
        t.putDouble("earned", earned);
        t.putInt("phase", phase);
        t.putBoolean("active", active);
        t.putBoolean("healing", healing);
        t.putInt("forgers", forgers);
        t.putInt("troops", troops);
        ListTag rl = new ListTag();
        for (RankRecord r : ranks) {
            CompoundTag rt = new CompoundTag();
            rt.putInt("rank", r.rank.ordinal());
            rt.putString("name", r.name);
            rt.putBoolean("alive", r.alive);
            rl.add(rt);
        }
        t.put("ranks", rl);
        ListTag items = new ListTag();
        for (ItemStack s : relic) if (!s.isEmpty()) items.add(s.save(reg));
        t.put("relic", items);
        return t;
    }

    static Faction load(CompoundTag t, HolderLookup.Provider reg) {
        Faction f = new Faction();
        f.id = t.getInt("id");
        f.name = t.getString("name");
        f.center = BlockPos.of(t.getLong("center"));
        f.awakenedAt = t.getLong("awakened");
        f.essence = t.getDouble("essence");
        f.earned = t.getDouble("earned");
        f.phase = t.getInt("phase");
        f.active = t.getBoolean("active");
        f.healing = t.getBoolean("healing");
        f.forgers = t.getInt("forgers");
        f.troops = t.getInt("troops");
        ListTag rl = t.getList("ranks", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag rt = rl.getCompound(i);
            RankRecord r = new RankRecord(InvasionRank.byId(rt.getInt("rank")), rt.getString("name"));
            r.alive = rt.getBoolean("alive");
            f.ranks.add(r);
        }
        ListTag items = t.getList("relic", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) ItemStack.parse(reg, items.getCompound(i)).ifPresent(f.relic::add);
        return f;
    }
}

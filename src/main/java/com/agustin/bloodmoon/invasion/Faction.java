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
    public int nextRankUid = 1;
    /** Hasta este tick el Dominio avanza a la mitad (murió un General). */
    public long slowedUntil;
    /** Interregno: tras la caída del Rey, el Dominio no gana tierra hasta este tick. */
    public long haltedUntil;
    /** La Ofrenda (fase 4): etapa (ver {@link FirstSoul}), chunk del santuario, altura del estrado, esencia dada, bloques del cristal puestos. */
    public int soulStage, soulY, soulPlaced, soulWarned;
    public long soulSite = RankRecord.NO_SEAT;
    public double soulProgress;
    public final List<RankRecord> ranks = new ArrayList<>();
    /** Lo que el Dominio se tragó de cofres y barriles: vuelve al vencerlo. */
    public final List<ItemStack> relic = new ArrayList<>();
    /** Cache de chunks con agua (no se guarda). */
    final Map<Long, Boolean> water = new HashMap<>();
    /** Caché: ¿el chunk tiene agua en más de un cuarto de su superficie? */
    final Map<Long, Boolean> wet = new HashMap<>();

    public static final class RankRecord {
        public static final long NO_SEAT = Long.MIN_VALUE;
        public int uid;
        public InvasionRank rank;
        public String name;
        public boolean alive = true;
        /** Chunk donde tiene su puesto (un obelisco para los Capitanes). */
        public long seat = NO_SEAT;
        /** Vida guardada mientras no tiene cuerpo (0-1). */
        public float hp = 1F;
        public long diedAt;

        RankRecord(InvasionRank rank, String name) {
            this.rank = rank;
            this.name = name;
        }
    }

    RankRecord newRank(InvasionRank rank, String name) {
        RankRecord r = new RankRecord(rank, name);
        r.uid = nextRankUid++;
        ranks.add(r);
        return r;
    }

    public RankRecord rankByUid(int uid) {
        for (RankRecord r : ranks) if (r.uid == uid) return r;
        return null;
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
        t.putInt("nextRankUid", nextRankUid);
        t.putLong("slowedUntil", slowedUntil);
        t.putLong("haltedUntil", haltedUntil);
        t.putInt("soulStage", soulStage);
        t.putInt("soulY", soulY);
        t.putInt("soulPlaced", soulPlaced);
        t.putInt("soulWarned", soulWarned);
        t.putLong("soulSite", soulSite);
        t.putDouble("soulProgress", soulProgress);
        ListTag rl = new ListTag();
        for (RankRecord r : ranks) {
            CompoundTag rt = new CompoundTag();
            rt.putInt("rank", r.rank.ordinal());
            rt.putString("name", r.name);
            rt.putBoolean("alive", r.alive);
            rt.putInt("uid", r.uid);
            rt.putLong("seat", r.seat);
            rt.putFloat("hp", r.hp);
            rt.putLong("diedAt", r.diedAt);
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
        f.nextRankUid = Math.max(1, t.getInt("nextRankUid"));
        f.slowedUntil = t.getLong("slowedUntil");
        f.haltedUntil = t.getLong("haltedUntil");
        f.soulStage = t.getInt("soulStage");
        f.soulY = t.getInt("soulY");
        f.soulPlaced = t.getInt("soulPlaced");
        f.soulWarned = t.getInt("soulWarned");
        f.soulSite = t.contains("soulSite") ? t.getLong("soulSite") : RankRecord.NO_SEAT;
        f.soulProgress = t.getDouble("soulProgress");
        ListTag rl = t.getList("ranks", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag rt = rl.getCompound(i);
            RankRecord r = new RankRecord(InvasionRank.byId(rt.getInt("rank")), rt.getString("name"));
            r.alive = rt.getBoolean("alive");
            r.uid = rt.contains("uid") ? rt.getInt("uid") : f.nextRankUid++;
            r.seat = rt.contains("seat") ? rt.getLong("seat") : RankRecord.NO_SEAT;
            r.hp = rt.contains("hp") ? rt.getFloat("hp") : 1F;
            r.diedAt = rt.getLong("diedAt");
            f.ranks.add(r);
        }
        ListTag items = t.getList("relic", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) ItemStack.parse(reg, items.getCompound(i)).ifPresent(f.relic::add);
        return f;
    }
}

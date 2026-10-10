package com.agustin.bloodmoon.human;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Un asentamiento humano como datos: población, comida, tesoro (en cobre), nivel y las obras que levantó después
 * de nacer. Los edificios originales se recalculan del plano (determinista), así que no se guardan.
 */
public final class Settlement {
    public static final int VILLAGE = 0, TOWN = 1;

    /** Obra hecha en partida. state: 0 en construcción, 1 terminada (cuenta como vivienda) pero sin colocar, 2 en pie. */
    public static final class Work {
        public VillageLayout.Building b;
        public VillageLayout.Road spur;
        public int arm;
        public double d;
        public int state;
        public int placed;
        public boolean prepared;
    }

    public long key;
    public int x, y, z;
    public long seed;
    public Culture culture = Culture.PLAINS;
    public String name = "";
    public boolean colony;
    public long parent;
    public int foundedDay;

    public int pop;
    public double food;
    public double treasury;
    public int level;
    public int materialized;
    public double growth;
    public double progress;
    public int lastColonyDay = -100;
    /** Siguiente edificio del plano original que levanta una colonia (las aldeas del mundo ya lo tienen todo). */
    public int colonyNext;
    /** Ciclos de espera tras no encontrar lote (no se guarda). */
    public transient int plotWait;
    /** Tramos de la red de calles ya pavimentados. */
    public boolean[] paved = new boolean[0];
    public final List<Work> works = new ArrayList<>();
    /** Lotes descartados (el jugador construyó ahí): no se vuelven a intentar. */
    public final List<VillageLayout.Building> blocked = new ArrayList<>();

    public VillageSites.Site site() {
        return new VillageSites.Site(x, y, z, seed, culture);
    }

    public Work current() {
        for (Work w : works) if (w.state == 0) return w;
        return null;
    }

    // ------------------------------------------------------------------ guardado

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("key", key);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putLong("seed", seed);
        t.putInt("culture", culture.ordinal());
        t.putString("name", name);
        t.putBoolean("colony", colony);
        t.putLong("parent", parent);
        t.putInt("founded", foundedDay);
        t.putInt("pop", pop);
        t.putDouble("food", food);
        t.putDouble("treasury", treasury);
        t.putInt("level", level);
        t.putInt("mat", materialized);
        t.putDouble("growth", growth);
        t.putDouble("progress", progress);
        t.putInt("lastColony", lastColonyDay);
        t.putInt("colonyNext", colonyNext);
        byte[] pv = new byte[paved.length];
        for (int i = 0; i < pv.length; i++) pv[i] = (byte) (paved[i] ? 1 : 0);
        t.putByteArray("paved", pv);
        ListTag ws = new ListTag();
        for (Work w : works) {
            CompoundTag c = building(w.b);
            c.put("spur", road(w.spur));
            c.putInt("arm", w.arm);
            c.putDouble("d", w.d);
            c.putInt("state", w.state);
            c.putInt("placed", w.placed);
            c.putBoolean("prepared", w.prepared);
            ws.add(c);
        }
        t.put("works", ws);
        ListTag bl = new ListTag();
        for (VillageLayout.Building b : blocked) bl.add(building(b));
        t.put("blocked", bl);
        return t;
    }

    public static Settlement load(CompoundTag t) {
        Settlement s = new Settlement();
        s.key = t.getLong("key");
        s.x = t.getInt("x");
        s.y = t.getInt("y");
        s.z = t.getInt("z");
        s.seed = t.getLong("seed");
        s.culture = Culture.byId(t.getInt("culture"));
        s.name = t.getString("name");
        s.colony = t.getBoolean("colony");
        s.parent = t.getLong("parent");
        s.foundedDay = t.getInt("founded");
        s.pop = t.getInt("pop");
        s.food = t.getDouble("food");
        s.treasury = t.getDouble("treasury");
        s.level = t.getInt("level");
        s.materialized = t.getInt("mat");
        s.growth = t.getDouble("growth");
        s.progress = t.getDouble("progress");
        s.lastColonyDay = t.getInt("lastColony");
        s.colonyNext = t.getInt("colonyNext");
        byte[] pv = t.getByteArray("paved");
        s.paved = new boolean[pv.length];
        for (int i = 0; i < pv.length; i++) s.paved[i] = pv[i] != 0;
        for (Tag e : t.getList("works", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Work w = new Work();
            w.b = building(c);
            w.spur = road(c.getCompound("spur"));
            w.arm = c.getInt("arm");
            w.d = c.getDouble("d");
            w.state = c.getInt("state");
            w.placed = c.getInt("placed");
            w.prepared = c.getBoolean("prepared");
            s.works.add(w);
        }
        for (Tag e : t.getList("blocked", Tag.TAG_COMPOUND)) s.blocked.add(building((CompoundTag) e));
        return s;
    }

    static CompoundTag building(VillageLayout.Building b) {
        CompoundTag c = new CompoundTag();
        c.putString("t", b.template());
        c.putInt("kind", b.kind().ordinal());
        c.putInt("job", b.job().ordinal());
        c.putInt("res", b.residents());
        c.putIntArray("geo", new int[]{b.x(), b.z(), b.rot().ordinal(), b.floorY(), b.minX(), b.minZ(), b.maxX(), b.maxZ(), b.coreX(), b.coreZ()});
        return c;
    }

    static VillageLayout.Building building(CompoundTag c) {
        int[] g = c.getIntArray("geo");
        if (g.length < 10) g = new int[10];
        return new VillageLayout.Building(c.getString("t"), VillageLayout.Kind.values()[c.getInt("kind")], HumanJob.byId(c.getInt("job")),
                c.getInt("res"), g[0], g[1], Rotation.values()[g[2]], g[3], g[4], g[5], g[6], g[7], g[8], g[9]);
    }

    static CompoundTag road(VillageLayout.Road r) {
        CompoundTag c = new CompoundTag();
        if (r == null) return c;
        c.putDouble("x0", r.x0());
        c.putDouble("z0", r.z0());
        c.putDouble("x1", r.x1());
        c.putDouble("z1", r.z1());
        c.putDouble("h", r.half());
        c.putInt("y0", r.y0());
        c.putInt("y1", r.y1());
        return c;
    }

    static VillageLayout.Road road(CompoundTag c) {
        if (!c.contains("h")) return null;
        int y0 = c.contains("y0") ? c.getInt("y0") : Integer.MIN_VALUE, y1 = c.contains("y1") ? c.getInt("y1") : Integer.MIN_VALUE;
        return new VillageLayout.Road(c.getDouble("x0"), c.getDouble("z0"), c.getDouble("x1"), c.getDouble("z1"), c.getDouble("h"), y0, y1);
    }
}

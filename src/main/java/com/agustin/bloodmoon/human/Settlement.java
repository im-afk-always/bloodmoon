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
    public static final int VILLAGE = 0, TOWN = 1, CITY = 2, CAPITAL = 3;

    /** Obra hecha en partida. state: 0 en construcción, 1 terminada (cuenta como vivienda) pero sin colocar, 2 en pie. */
    public static final class Work {
        public VillageLayout.Building b;
        public VillageLayout.Road spur;
        public int arm;
        public double d;
        public int state;
        public int placed;
        public boolean prepared;
        /** Edificio que reemplaza: índice del plano original (>= 0), obra -(i + 2), o -1 ninguno. */
        public int replaces = -1;
        /** Solo demoler (sin edificio nuevo): granjas que el centro de la ciudad ya no tiene. */
        public boolean demolishOnly;
        public double progress;
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
    /** Calles abiertas al crecer (se suman a la red del plano): tramo, tramo padre (índice en la red combinada) y distancia. */
    public final List<VillageLayout.Road> extraNet = new ArrayList<>();
    public int[] extraParent = new int[0];
    public double[] extraDist = new double[0];
    public boolean plazaStone;
    /** Economía: stock y precio (cobre por unidad) de cada bien; movimiento de caravanas y tratos con jugadores. */
    public double[] stock = new double[0];
    public double[] price = new double[0];
    public transient double exported, imported;
    public int playerTrades;
    /** Puerto: orilla, dirección hacia el agua, altura del agua, tamaño del cuerpo de agua (celdas), muelles. */
    public boolean portChecked, portLighthouse;
    public int portX, portZ, portDX, portDZ, portY, portSize, portPiers;
    public boolean[] pierDone = new boolean[0];
    public final List<Work> works = new ArrayList<>();
    /** Lotes descartados (el jugador construyó ahí): no se vuelven a intentar. */
    public final List<VillageLayout.Building> blocked = new ArrayList<>();
    /** Edificios del plano original ya demolidos (o en vías de) por la renovación. */
    public boolean[] removed = new boolean[0];
    /** Edificios viejos donde el reemplazo de piedra no entra (se quedan como están). */
    public final java.util.Set<Long> skipRenew = new java.util.HashSet<>();
    /** Calles empedradas (ciudad): un tramo por índice de la red; el último es la plaza. */
    public boolean[] stone = new boolean[0];
    public int streetTier;
    /** Muralla: tramos planificados, avance como datos (0-1) y tramos ya levantados. */
    public final List<VillageLayout.Road> wall = new ArrayList<>();
    public double wallProgress;
    public boolean[] wallDone = new boolean[0];

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
            c.putInt("replaces", w.replaces);
            c.putBoolean("demolish", w.demolishOnly);
            c.putDouble("progress", w.progress);
            ws.add(c);
        }
        t.put("works", ws);
        ListTag bl = new ListTag();
        for (VillageLayout.Building b : blocked) bl.add(building(b));
        t.put("blocked", bl);
        t.putByteArray("removed", bytes(removed));
        t.putByteArray("stone", bytes(stone));
        t.putInt("streetTier", streetTier);
        ListTag xn = new ListTag();
        for (VillageLayout.Road r : extraNet) xn.add(road(r));
        t.put("extraNet", xn);
        t.putIntArray("extraParent", extraParent);
        long[] xd = new long[extraDist.length];
        for (int i = 0; i < xd.length; i++) xd[i] = Double.doubleToLongBits(extraDist[i]);
        t.putLongArray("extraDist", xd);
        t.putBoolean("plazaStone", plazaStone);
        t.putLongArray("stock", java.util.Arrays.stream(stock).mapToLong(Double::doubleToLongBits).toArray());
        t.putLongArray("price", java.util.Arrays.stream(price).mapToLong(Double::doubleToLongBits).toArray());
        t.putInt("playerTrades", playerTrades);
        t.putBoolean("portChecked", portChecked);
        t.putBoolean("portLighthouse", portLighthouse);
        t.putIntArray("port", new int[]{portX, portZ, portDX, portDZ, portY, portSize, portPiers});
        t.putByteArray("pierDone", bytes(pierDone));
        t.putLongArray("skipRenew", skipRenew.stream().mapToLong(Long::longValue).toArray());
        ListTag wl = new ListTag();
        for (VillageLayout.Road r : wall) wl.add(road(r));
        t.put("wall", wl);
        t.putDouble("wallProgress", wallProgress);
        t.putByteArray("wallDone", bytes(wallDone));
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
            w.replaces = c.contains("replaces") ? c.getInt("replaces") : -1;
            w.demolishOnly = c.getBoolean("demolish");
            w.progress = c.getDouble("progress");
            s.works.add(w);
        }
        for (Tag e : t.getList("blocked", Tag.TAG_COMPOUND)) s.blocked.add(building((CompoundTag) e));
        s.removed = bools(t.getByteArray("removed"));
        s.stone = bools(t.getByteArray("stone"));
        s.streetTier = t.getInt("streetTier");
        for (Tag e : t.getList("extraNet", Tag.TAG_COMPOUND)) s.extraNet.add(road((CompoundTag) e));
        s.extraParent = t.getIntArray("extraParent");
        long[] xd = t.getLongArray("extraDist");
        s.extraDist = new double[xd.length];
        for (int i = 0; i < xd.length; i++) s.extraDist[i] = Double.longBitsToDouble(xd[i]);
        s.plazaStone = t.getBoolean("plazaStone");
        s.stock = java.util.Arrays.stream(t.getLongArray("stock")).mapToDouble(Double::longBitsToDouble).toArray();
        s.price = java.util.Arrays.stream(t.getLongArray("price")).mapToDouble(Double::longBitsToDouble).toArray();
        s.playerTrades = t.getInt("playerTrades");
        s.portChecked = t.getBoolean("portChecked");
        s.portLighthouse = t.getBoolean("portLighthouse");
        int[] pt = t.getIntArray("port");
        if (pt.length >= 7) {
            s.portX = pt[0]; s.portZ = pt[1]; s.portDX = pt[2]; s.portDZ = pt[3]; s.portY = pt[4]; s.portSize = pt[5]; s.portPiers = pt[6];
        }
        s.pierDone = bools(t.getByteArray("pierDone"));
        for (long k : t.getLongArray("skipRenew")) s.skipRenew.add(k);
        for (Tag e : t.getList("wall", Tag.TAG_COMPOUND)) s.wall.add(road((CompoundTag) e));
        s.wallProgress = t.getDouble("wallProgress");
        s.wallDone = bools(t.getByteArray("wallDone"));
        return s;
    }

    static byte[] bytes(boolean[] a) {
        byte[] o = new byte[a.length];
        for (int i = 0; i < a.length; i++) o[i] = (byte) (a[i] ? 1 : 0);
        return o;
    }

    static boolean[] bools(byte[] a) {
        boolean[] o = new boolean[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i] != 0;
        return o;
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

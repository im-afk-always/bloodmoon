package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.item.CoinItem;
import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.Optional;

/**
 * Economía de un asentamiento (Fase 3): stock y precio de cada {@link Good}. Los oficios producen, la población
 * consume, las obras gastan madera y piedra (lo que falta se importa más caro) y el precio sigue a la escasez
 * ({@code base × (objetivo / stock)^0,6}, entre 0,3× y 4×, suavizado). Las caravanas llevan bienes de donde sobran a
 * donde faltan. El jugador comercia a precio local: lo que compra sale del stock y su dinero entra al tesoro; lo que
 * vende entra al stock y se paga con monedas del tesoro (si el tesoro no alcanza, no compran).
 */
public final class Market {
    public static final int N = Good.values().length;

    private Market() {}

    static void ensure(Settlement s) {
        if (s.stock.length != N) {
            double[] st = java.util.Arrays.copyOf(s.stock, N);
            double[] pr = java.util.Arrays.copyOf(s.price, N);
            for (Good g : Good.values()) {
                if (pr[g.ordinal()] <= 0) pr[g.ordinal()] = g.base;
                if (st[g.ordinal()] <= 0) st[g.ordinal()] = 20;
            }
            s.stock = st;
            s.price = pr;
        }
    }

    private static int jobs(List<VillageLayout.Building> bs, HumanJob j) {
        int n = 0;
        for (VillageLayout.Building b : bs) if (b.kind() == VillageLayout.Kind.WORK && b.job() == j) n += HumanityManager.city(b) ? 2 : 1;
        return n;
    }

    private static int guards(List<VillageLayout.Building> bs) {
        int n = 0;
        for (VillageLayout.Building b : bs) {
            if (b.kind() == VillageLayout.Kind.TOWER) n += HumanityManager.city(b) ? 3 : 2;
            if (b.kind() == VillageLayout.Kind.CASTLE) n += 6;
            if (b.kind() == VillageLayout.Kind.HALL) n += 2;
        }
        return n;
    }

    /** Producción por día de cada bien (la comida la lleva aparte {@link HumanityManager#production}). */
    static double[] production(Settlement s, List<VillageLayout.Building> bs) {
        double[] p = new double[N];
        int pop = s.pop;
        boolean desert = s.culture == Culture.DESERT;
        int smiths = jobs(bs, HumanJob.TOOLSMITH) + jobs(bs, HumanJob.ARMORER) + jobs(bs, HumanJob.WEAPONSMITH);
        p[Good.WOOD.ordinal()] = 2 + pop * (desert ? 0.15 : 0.3);
        p[Good.STONE.ordinal()] = 2 + pop * 0.15 + jobs(bs, HumanJob.MASON) * 5;
        p[Good.IRON.ordinal()] = 0.5 + pop * 0.03 + smiths * 0.5;
        p[Good.TOOLS.ordinal()] = jobs(bs, HumanJob.TOOLSMITH) * 1.5;
        p[Good.WEAPONS.ordinal()] = jobs(bs, HumanJob.WEAPONSMITH) * 1.2 + jobs(bs, HumanJob.FLETCHER) * 0.8;
        p[Good.ARMOR.ordinal()] = jobs(bs, HumanJob.ARMORER) * 0.8;
        p[Good.CLOTH.ordinal()] = 0.5 + pop * 0.04 + jobs(bs, HumanJob.SHEPHERD) * 4;
        p[Good.LEATHER.ordinal()] = jobs(bs, HumanJob.BUTCHER) * 1.5 + jobs(bs, HumanJob.LEATHERWORKER) * 2.5;
        p[Good.BOOKS.ordinal()] = jobs(bs, HumanJob.LIBRARIAN) + jobs(bs, HumanJob.CARTOGRAPHER) * 0.5;
        int markets = 0, stalls = 0, castles = 0;
        for (VillageLayout.Building b : bs) {
            if (b.kind() == VillageLayout.Kind.MARKET) markets++;
            if (b.kind() == VillageLayout.Kind.STALL) stalls++;
            if (b.kind() == VillageLayout.Kind.CASTLE) castles++;
        }
        p[Good.GOLD.ordinal()] = 0.1 + castles * 0.5;
        p[Good.LUXURY.ordinal()] = jobs(bs, HumanJob.CLERIC) * 0.4 + markets + stalls * 0.2;
        return p;
    }

    /** Consumo por día (sin la comida). */
    static double[] consumption(Settlement s, List<VillageLayout.Building> bs) {
        double[] c = new double[N];
        int pop = s.pop;
        int g = guards(bs);
        c[Good.WOOD.ordinal()] = pop * 0.08;
        c[Good.STONE.ordinal()] = pop * 0.05;
        c[Good.IRON.ordinal()] = jobs(bs, HumanJob.TOOLSMITH) + jobs(bs, HumanJob.WEAPONSMITH) * 0.6 + jobs(bs, HumanJob.ARMORER) + pop * 0.01;
        c[Good.TOOLS.ordinal()] = pop * 0.04;
        c[Good.WEAPONS.ordinal()] = g * 0.15 + pop * 0.005;
        c[Good.ARMOR.ordinal()] = g * 0.08;
        c[Good.CLOTH.ordinal()] = pop * 0.08;
        c[Good.LEATHER.ordinal()] = pop * 0.04;
        c[Good.BOOKS.ordinal()] = pop * (s.level >= Settlement.TOWN ? 0.015 : 0.005);
        c[Good.GOLD.ordinal()] = pop * 0.005;
        c[Good.LUXURY.ordinal()] = pop * 0.02 * (1 + s.level);
        return c;
    }

    static double target(Settlement s, double consumption) {
        return Math.max(8, consumption * 10);
    }

    /** Un ciclo (1/20 de día): producción, consumo y precios. */
    public static void cycle(Settlement s, List<VillageLayout.Building> bs) {
        ensure(s);
        double[] p = production(s, bs), c = consumption(s, bs);
        for (Good g : Good.values()) {
            int i = g.ordinal();
            if (g == Good.FOOD) {
                s.stock[i] = s.food;
                double target = target(s, s.pop);
                updatePrice(s, g, target);
                continue;
            }
            s.stock[i] = Math.max(0, s.stock[i] + (p[i] - c[i]) / HumanityManager.CYCLES_PER_DAY);
            double target = target(s, c[i]);
            s.stock[i] = Math.min(s.stock[i], target * 4 + 20);
            updatePrice(s, g, target);
        }
    }

    private static void updatePrice(Settlement s, Good g, double target) {
        int i = g.ordinal();
        double raw = g.base * Math.pow(target / (s.stock[i] + 1), 0.6);
        raw = Math.max(g.base * 0.3, Math.min(g.base * 4.0, raw));
        s.price[i] += (raw - s.price[i]) * 0.1;
    }

    public static double price(Settlement s, Good g) {
        ensure(s);
        return s.price[g.ordinal()];
    }

    /** Las herramientas hacen rendir el campo: sin ellas la producción de comida cae hasta un 25 %. */
    public static double toolFactor(Settlement s, List<VillageLayout.Building> bs) {
        ensure(s);
        double t = target(s, consumption(s, bs)[Good.TOOLS.ordinal()]);
        return 0.75 + 0.25 * Math.min(1, s.stock[Good.TOOLS.ordinal()] / t);
    }

    /** Ropa y lujos: sin ellos la gente tiene menos hijos (hasta un 40 % menos). */
    public static double comfortFactor(Settlement s, List<VillageLayout.Building> bs) {
        ensure(s);
        double[] c = consumption(s, bs);
        double cloth = Math.min(1, s.stock[Good.CLOTH.ordinal()] / target(s, c[Good.CLOTH.ordinal()]));
        double lux = Math.min(1, s.stock[Good.LUXURY.ordinal()] / target(s, c[Good.LUXURY.ordinal()]));
        return 0.6 + 0.25 * cloth + 0.15 * lux;
    }

    // ------------------------------------------------------------------ obras: materiales

    /** {madera, piedra} que lleva un edificio. */
    static int[] materials(VillageLayout.Building b) {
        boolean c = HumanityManager.city(b);
        String t = b.template();
        if (t.endsWith("city_work_librarian")) return new int[]{140, 360};
        if (t.endsWith("work_librarian")) return new int[]{80, 90};
        return switch (b.kind()) {
            case HOUSE -> c ? new int[]{40, 120} : t.contains("large") ? new int[]{50, 20} : new int[]{30, 10};
            case WORK -> c ? new int[]{45, 120} : new int[]{30, 15};
            case TOWER -> c ? new int[]{20, 150} : new int[]{30, 40};
            case STALL -> new int[]{10, 0};
            case FARM -> new int[]{10, 0};
            case WELL -> c ? new int[]{0, 60} : new int[]{0, 20};
            case MARKET -> new int[]{60, 150};
            case HALL -> new int[]{80, 300};
            case CASTLE -> new int[]{100, 900};
        };
    }

    /** Costo extra (cobre) de importar los materiales que faltan en el stock. */
    static double importCost(Settlement s, VillageLayout.Building b) {
        ensure(s);
        int[] m = materials(b);
        double cost = 0;
        cost += Math.max(0, m[0] - s.stock[Good.WOOD.ordinal()]) * s.price[Good.WOOD.ordinal()] * 1.3;
        cost += Math.max(0, m[1] - s.stock[Good.STONE.ordinal()]) * s.price[Good.STONE.ordinal()] * 1.3;
        return cost;
    }

    static void consumeMaterials(Settlement s, VillageLayout.Building b) {
        int[] m = materials(b);
        s.stock[Good.WOOD.ordinal()] = Math.max(0, s.stock[Good.WOOD.ordinal()] - m[0]);
        s.stock[Good.STONE.ordinal()] = Math.max(0, s.stock[Good.STONE.ordinal()] - m[1]);
    }

    // ------------------------------------------------------------------ caravanas

    /**
     * Comercio entre asentamientos (una vez por día): cada bien viaja de donde es barato a donde es caro si la
     * diferencia paga el viaje. El que vende cobra su precio; el que compra paga el suyo (la diferencia es del mercader).
     */
    public static void caravans(ServerLevel level, HumanityManager.Data data) {
        List<Settlement> all = new java.util.ArrayList<>(data.settlements.values());
        for (Settlement s : all) {
            ensure(s);
            s.exported = 0;
            s.imported = 0;
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = 0; j < all.size(); j++) {
                if (i == j) continue;
                Settlement a = all.get(i), b = all.get(j);
                double dist = Math.hypot(a.x - b.x, a.z - b.z);
                if (dist > 1500) continue;
                double margin = 1.25 + dist / 4000;
                for (Good g : Good.values()) {
                    if (g == Good.FOOD) continue;
                    int k = g.ordinal();
                    double pa = a.price[k], pb = b.price[k];
                    if (pb < pa * margin) continue;
                    double surplus = a.stock[k] - 15;
                    double want = Math.max(0, 40 - b.stock[k]);
                    double qty = Math.min(30, Math.min(surplus, want));
                    if (qty < 2) continue;
                    double pay = qty * pb;
                    if (b.treasury < pay) continue;
                    a.stock[k] -= qty;
                    b.stock[k] += qty;
                    a.treasury += qty * pa;
                    b.treasury -= pay;
                    a.exported += qty;
                    b.imported += qty;
                }
            }
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ monedas

    public static int value(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof CoinItem c)) return 0;
        return c.value * s.getCount();
    }

    public static int value(ItemCost c) {
        return value(c.itemStack());
    }

    public static boolean coin(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof CoinItem;
    }

    /** Monedas para cobrar {@code copper}: oro + plata (en B si está libre), o una sola denominación. */
    public static ItemCost[] payment(int copper, boolean bFree) {
        copper = Math.max(1, copper);
        if (copper < 10) return new ItemCost[]{new ItemCost(ModItems.COPPER_COIN.get(), copper)};
        int silver = Math.round(copper / 10f);
        if (silver <= 64 && (silver < 10 || !bFree || silver % 10 == 0)) {
            if (silver % 10 == 0 && silver >= 10) return new ItemCost[]{new ItemCost(ModItems.GOLD_COIN.get(), silver / 10)};
            return new ItemCost[]{new ItemCost(ModItems.SILVER_COIN.get(), silver)};
        }
        int gold = Math.min(64, silver / 10);
        int rest = silver - gold * 10;
        if (bFree && rest > 0 && rest <= 64) {
            return new ItemCost[]{new ItemCost(ModItems.GOLD_COIN.get(), gold), new ItemCost(ModItems.SILVER_COIN.get(), rest)};
        }
        return new ItemCost[]{new ItemCost(ModItems.GOLD_COIN.get(), Math.max(1, Math.round(silver / 10f)))};
    }

    /** Monedas para pagar {@code copper} en un solo montón. */
    public static ItemStack payout(int copper) {
        copper = Math.max(1, copper);
        if (copper < 10) return new ItemStack(ModItems.COPPER_COIN.get(), copper);
        if (copper <= 640) return new ItemStack(ModItems.SILVER_COIN.get(), Math.max(1, Math.round(copper / 10f)));
        return new ItemStack(ModItems.GOLD_COIN.get(), Math.min(64, Math.max(1, Math.round(copper / 100f))));
    }

    // ------------------------------------------------------------------ comercio con el jugador

    /** Valor en cobre del lado "monedas" de una oferta (lo que se paga o se cobra). */
    public static int coinSide(MerchantOffer o) {
        if (coin(o.getResult())) return value(o.getResult());
        int v = value(o.getItemCostA());
        if (o.getItemCostB().isPresent()) v += value(o.getItemCostB().get());
        return v;
    }

    /**
     * Ajusta cada oferta al precio local: el humano vende más caro si su pueblo tiene poco de ese bien y paga menos si
     * tiene mucho. Sin stock para venderte o sin tesoro para comprarte, la oferta aparece agotada.
     */
    public static void reprice(Human h, Settlement s, int[] base) {
        MerchantOffers offers = h.getOffers();
        ensure(s);
        for (int i = 0; i < offers.size() && i < base.length; i++) {
            MerchantOffer o = offers.get(i);
            if (base[i] <= 0) continue;
            boolean sells = !coin(o.getResult());               // el humano vende (el jugador paga monedas)
            ItemStack goods = sells ? o.getResult() : o.getItemCostA().itemStack();
            Good g = Good.of(goods);
            if (g == null) continue;
            double f = s.price[g.ordinal()] / g.base;
            int v = (int) Math.round(base[i] * Math.max(0.3, Math.min(4, f)) * (sells ? 1.0 : 0.9));
            MerchantOffer n;
            if (sells) {
                boolean bFree = o.getItemCostB().isEmpty() || coin(o.getItemCostB().get().itemStack());
                ItemCost[] pay = payment(v, bFree);
                Optional<ItemCost> b = pay.length > 1 ? Optional.of(pay[1]) : (bFree ? Optional.empty() : o.getItemCostB());
                boolean out = s.stock[g.ordinal()] < goods.getCount();
                n = new MerchantOffer(pay[0], b, o.getResult().copy(), out ? o.getMaxUses() : o.getUses(), o.getMaxUses(), o.getXp(),
                        o.getPriceMultiplier(), o.getDemand());
            } else {
                boolean broke = s.treasury < v;
                n = new MerchantOffer(o.getItemCostA(), o.getItemCostB(), payout(v), broke ? o.getMaxUses() : o.getUses(), o.getMaxUses(),
                        o.getXp(), o.getPriceMultiplier(), o.getDemand());
            }
            offers.set(i, n);
        }
    }

    /** Lo que deja un trato en el asentamiento: bienes que salen o entran y monedas que entran o salen del tesoro. */
    public static void onTrade(Settlement s, MerchantOffer o) {
        ensure(s);
        boolean sells = !coin(o.getResult());
        ItemStack goods = sells ? o.getResult() : o.getItemCostA().itemStack();
        Good g = Good.of(goods);
        int v = coinSide(o);
        if (sells) {
            s.treasury += v;
            if (g != null) s.stock[g.ordinal()] = Math.max(0, s.stock[g.ordinal()] - goods.getCount());
        } else {
            s.treasury = Math.max(0, s.treasury - v);
            if (g != null) s.stock[g.ordinal()] += goods.getCount();
        }
        s.playerTrades++;
    }

    /** Bienes que el mercader compra y vende al por mayor, a precio del día. */
    private static final Object[][] BULK = {
            {Good.FOOD, Items.WHEAT, 16, Items.BREAD, 6},
            {Good.WOOD, Items.OAK_LOG, 16, Items.OAK_LOG, 16},
            {Good.STONE, Items.COBBLESTONE, 32, Items.STONE_BRICKS, 16},
            {Good.IRON, Items.IRON_INGOT, 4, Items.IRON_INGOT, 4},
            {Good.CLOTH, Items.WHITE_WOOL, 8, Items.WHITE_WOOL, 8},
            {Good.LEATHER, Items.LEATHER, 6, Items.LEATHER, 6},
            {Good.BOOKS, Items.BOOK, 2, Items.BOOK, 2},
            {Good.GOLD, Items.GOLD_INGOT, 2, Items.GOLD_INGOT, 2},
    };

    public static void merchantOffers(Settlement s, MerchantOffers out) {
        ensure(s);
        for (Object[] row : BULK) {
            Good g = (Good) row[0];
            double p = s.price[g.ordinal()];
            Item buyItem = (Item) row[1];
            int buyN = (Integer) row[2];
            Item sellItem = (Item) row[3];
            int sellN = (Integer) row[4];
            // el jugador vende
            int pay = (int) Math.floor(buyN * p * 0.9);
            if (pay >= 1) {
                boolean broke = s.treasury < pay;
                out.add(new MerchantOffer(new ItemCost(buyItem, buyN), Optional.empty(), payout(pay), broke ? 16 : 0, 16, 1, 0f, 0));
            }
            // el jugador compra
            double units = sellItem == Items.BREAD ? sellN * 3 : sellN;
            int cost = (int) Math.ceil(units * p * 1.1);
            ItemCost[] c = payment(cost, true);
            boolean out0 = s.stock[g.ordinal()] < units;
            out.add(new MerchantOffer(c[0], c.length > 1 ? Optional.of(c[1]) : Optional.empty(), new ItemStack(sellItem, sellN),
                    out0 ? 16 : 0, 16, 1, 0f, 0));
        }
    }

    public static String report(Settlement s) {
        ensure(s);
        StringBuilder sb = new StringBuilder();
        for (Good g : Good.values()) {
            int i = g.ordinal();
            double f = s.price[i] / g.base;
            String arrow = f > 1.4 ? "▲" : f < 0.75 ? "▼" : "·";
            sb.append(' ').append(g.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append((int) s.stock[i])
                    .append(" @").append(String.format(java.util.Locale.ROOT, "%.1f", s.price[i])).append(arrow);
        }
        return sb.toString();
    }
}

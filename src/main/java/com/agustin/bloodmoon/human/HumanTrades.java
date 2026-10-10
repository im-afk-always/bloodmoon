package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.registry.ModItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ofertas de los humanos: las mismas listas de los aldeanos (todo lo que daban sigue disponible), pero pagadas en
 * monedas. Tipo de cambio: 1 esmeralda = 1 moneda de plata = 10 de cobre; 10 de plata = 1 de oro.
 */
public final class HumanTrades {
    private HumanTrades() {}

    public static VillagerProfession profession(HumanJob job) {
        if (job.vanilla == null) return VillagerProfession.NONE;
        return BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(job.vanilla));
    }

    /** Ofertas nuevas para el nivel dado (1-5): dos al azar de la lista de ese nivel, convertidas a monedas. */
    public static List<MerchantOffer> forLevel(Entity trader, HumanJob job, int level, RandomSource random) {
        List<MerchantOffer> out = new ArrayList<>();
        if (job == HumanJob.MERCHANT) {
            if (level == 1) {
                out.add(new MerchantOffer(new ItemCost(ModItems.COPPER_COIN.get(), 10), new ItemStack(ModItems.SILVER_COIN.get()), 9999, 0, 0f));
                out.add(new MerchantOffer(new ItemCost(ModItems.SILVER_COIN.get(), 10), new ItemStack(ModItems.GOLD_COIN.get()), 9999, 0, 0f));
                out.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.SILVER_COIN.get()), 9999, 0, 0f));
                out.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 3), new ItemStack(ModItems.SILVER_COIN.get()), 64, 1, 0.05f));
                out.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, 4), new ItemStack(ModItems.SILVER_COIN.get()), 64, 1, 0.05f));
                out.add(new MerchantOffer(new ItemCost(Items.DIAMOND, 1), new ItemStack(ModItems.SILVER_COIN.get(), 6), 32, 2, 0.05f));
            }
            return out;
        }
        VillagerProfession prof = profession(job);
        Int2ObjectMap<VillagerTrades.ItemListing[]> map = VillagerTrades.TRADES.get(prof);
        if (map == null) return out;
        VillagerTrades.ItemListing[] listings = map.get(level);
        if (listings == null || listings.length == 0) return out;
        List<VillagerTrades.ItemListing> pool = new ArrayList<>(List.of(listings));
        int want = 2;
        while (want > 0 && !pool.isEmpty()) {
            VillagerTrades.ItemListing listing = pool.remove(random.nextInt(pool.size()));
            MerchantOffer offer;
            try {
                offer = listing.getOffer(trader, random);
            } catch (RuntimeException e) {
                offer = null;
            }
            if (offer == null) continue;
            MerchantOffer c = toCoins(offer);
            if (c != null) {
                out.add(c);
                want--;
            }
        }
        return out;
    }

    /** Reemplaza esmeraldas por monedas (en el costo y en el resultado). */
    public static MerchantOffer toCoins(MerchantOffer o) {
        ItemCost a = o.getItemCostA();
        Optional<ItemCost> b = o.getItemCostB();
        ItemStack result = o.getResult().copy();

        if (result.is(Items.EMERALD)) {
            int n = result.getCount();
            result = n >= 10 && n % 10 == 0 ? new ItemStack(ModItems.GOLD_COIN.get(), n / 10)
                    : new ItemStack(ModItems.SILVER_COIN.get(), n);
        }
        boolean aEm = a.item().value() == Items.EMERALD;
        boolean bEm = b.isPresent() && b.get().item().value() == Items.EMERALD;
        if (aEm) {
            int n = a.count();
            if (n >= 10) {
                ItemCost gold = new ItemCost(ModItems.GOLD_COIN.get(), n / 10);
                int rest = n % 10;
                if (rest == 0) {
                    a = gold;
                } else if (b.isEmpty()) {
                    a = gold;
                    b = Optional.of(new ItemCost(ModItems.SILVER_COIN.get(), rest));
                } else {
                    a = new ItemCost(ModItems.SILVER_COIN.get(), n);
                }
            } else {
                a = new ItemCost(ModItems.SILVER_COIN.get(), n);
            }
        }
        if (bEm) b = Optional.of(new ItemCost(ModItems.SILVER_COIN.get(), b.get().count()));
        return new MerchantOffer(a, b, result, o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier(), o.getDemand());
    }
}

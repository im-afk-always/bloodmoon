package com.agustin.bloodmoon.human;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Bienes abstractos de la economía humana. Cada asentamiento tiene stock y precio de cada uno; los ítems del juego se
 * clasifican en un bien para que comprar o vender cosas reales mueva el mercado de ese lugar.
 * {@code base}: precio de referencia en cobre por unidad.
 */
public enum Good {
    FOOD(2), WOOD(2), STONE(1), IRON(8), TOOLS(25), WEAPONS(35), ARMOR(50), CLOTH(6), LEATHER(6), BOOKS(30), GOLD(60), LUXURY(40);

    public final int base;

    Good(int base) {
        this.base = base;
    }

    public String key() {
        return "good.bloodmoon." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Bien al que pertenece un ítem, o null si no forma parte de la economía (monedas, cosas raras). */
    public static Good of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item it = stack.getItem();
        if (it instanceof com.agustin.bloodmoon.item.CoinItem) return null;
        if (it == Items.LEATHER_HELMET || it == Items.LEATHER_CHESTPLATE || it == Items.LEATHER_LEGGINGS || it == Items.LEATHER_BOOTS) return LEATHER;
        if (it instanceof ArmorItem) return ARMOR;
        if (it instanceof ShieldItem) return ARMOR;
        if (it instanceof SwordItem || it instanceof BowItem || it instanceof CrossbowItem || it instanceof TridentItem
                || stack.is(ItemTags.ARROWS)) return WEAPONS;
        if (it instanceof DiggerItem || it instanceof ShearsItem || it instanceof FishingRodItem || it == Items.FLINT_AND_STEEL
                || it == Items.BUCKET || it == Items.COMPASS || it == Items.CLOCK) return TOOLS;
        if (stack.has(DataComponents.FOOD) || it == Items.WHEAT || it == Items.WHEAT_SEEDS || it == Items.SUGAR_CANE
                || it == Items.PUMPKIN || it == Items.MELON || it == Items.HAY_BLOCK || it == Items.EGG || it == Items.COCOA_BEANS) return FOOD;
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || it == Items.STICK || stack.is(ItemTags.WOODEN_SLABS)
                || stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.WOODEN_FENCES)) return WOOD;
        if (stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS) || stack.is(ItemTags.BEDS) || stack.is(ItemTags.BANNERS)
                || it == Items.STRING) return CLOTH;
        if (it == Items.LEATHER || it == Items.RABBIT_HIDE || it == Items.SADDLE || it == Items.LEATHER_HORSE_ARMOR) return LEATHER;
        if (it == Items.BOOK || it == Items.ENCHANTED_BOOK || it == Items.WRITABLE_BOOK || it == Items.PAPER || it == Items.MAP
                || it == Items.FILLED_MAP || it == Items.BOOKSHELF || it == Items.LECTERN || it == Items.NAME_TAG) return BOOKS;
        if (it == Items.GOLD_INGOT || it == Items.GOLD_NUGGET || it == Items.GOLD_BLOCK || it == Items.RAW_GOLD
                || it == Items.GOLDEN_CARROT || it == Items.GLISTERING_MELON_SLICE) return GOLD;
        if (it == Items.IRON_INGOT || it == Items.IRON_NUGGET || it == Items.RAW_IRON || it == Items.COPPER_INGOT || it == Items.COAL
                || it == Items.CHARCOAL || it == Items.IRON_BLOCK || it == Items.CHAIN || it == Items.LANTERN || it == Items.BELL
                || it == Items.CAMPFIRE || it == Items.IRON_BARS) return IRON;
        if (it == Items.EMERALD || it == Items.DIAMOND || it == Items.ENDER_PEARL || it == Items.GLOWSTONE || it == Items.GLOWSTONE_DUST
                || it == Items.EXPERIENCE_BOTTLE || it == Items.AMETHYST_SHARD || it == Items.REDSTONE || it == Items.LAPIS_LAZULI
                || it == Items.POTION || it == Items.GLASS_BOTTLE || it == Items.BREWING_STAND) return LUXURY;
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_BRICKS) || stack.is(ItemTags.TERRACOTTA)
                || it == Items.BRICK || it == Items.BRICKS || it == Items.GLASS || it == Items.GLASS_PANE || it == Items.CLAY_BALL
                || it == Items.STONE || it == Items.SAND || it == Items.GRAVEL || it == Items.FLINT || it == Items.QUARTZ
                || it == Items.DRIPSTONE_BLOCK || it == Items.POLISHED_ANDESITE || it == Items.POLISHED_DIORITE
                || it == Items.POLISHED_GRANITE || it == Items.TUFF || it == Items.CALCITE) return STONE;
        if (stack.is(ItemTags.DYEABLE)) return CLOTH;
        return LUXURY;
    }
}

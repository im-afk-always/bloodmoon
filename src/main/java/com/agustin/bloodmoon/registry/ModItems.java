package com.agustin.bloodmoon.registry;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.item.EndCompassItem;
import com.agustin.bloodmoon.item.ModTiers;
import com.agustin.bloodmoon.item.VoidMaulItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BloodMoonMod.MODID);

    public static final DeferredItem<BlockItem> VOID_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_STONE);
    public static final DeferredItem<BlockItem> VOID_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_BLOCK);
    public static final DeferredItem<BlockItem> DEAD_GRASS_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.DEAD_GRASS_BLOCK);
    public static final DeferredItem<BlockItem> BARREN_DIRT = ITEMS.registerSimpleBlockItem(ModBlocks.BARREN_DIRT);
    public static final DeferredItem<BlockItem> DEAD_GRASS = ITEMS.registerSimpleBlockItem(ModBlocks.DEAD_GRASS);
    public static final DeferredItem<BlockItem> CHARRED_LOG = ITEMS.registerSimpleBlockItem(ModBlocks.CHARRED_LOG);
    public static final DeferredItem<BlockItem> BLACK_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK);
    public static final DeferredItem<BlockItem> BLACK_ROCK_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK_BRICKS);
    public static final DeferredItem<BlockItem> CRACKED_BLACK_ROCK_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.CRACKED_BLACK_ROCK_BRICKS);
    public static final DeferredItem<BlockItem> POLISHED_BLACK_ROCK = ITEMS.registerSimpleBlockItem(ModBlocks.POLISHED_BLACK_ROCK);
    public static final DeferredItem<BlockItem> CHISELED_BLACK_ROCK_BRICKS = ITEMS.registerSimpleBlockItem(ModBlocks.CHISELED_BLACK_ROCK_BRICKS);
    public static final DeferredItem<BlockItem> BLACK_ROCK_PILLAR = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK_PILLAR);
    public static final DeferredItem<BlockItem> BLACK_ROCK_BRICK_STAIRS = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK_BRICK_STAIRS);
    public static final DeferredItem<BlockItem> BLACK_ROCK_BRICK_SLAB = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK_BRICK_SLAB);
    public static final DeferredItem<BlockItem> BLACK_ROCK_BRICK_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_ROCK_BRICK_WALL);
    public static final DeferredItem<BlockItem> VOID_CHAIN = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_CHAIN);
    public static final DeferredItem<BlockItem> VOID_GLASS_PANE = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_GLASS_PANE);
    public static final DeferredItem<BlockItem> VOID_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_LANTERN);
    public static final DeferredItem<BlockItem> OBELISK_CORE = ITEMS.registerSimpleBlockItem(ModBlocks.OBELISK_CORE);

    public static final DeferredItem<ArmorItem> VOID_HELMET = armor("void_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorItem> VOID_CHESTPLATE = armor("void_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorItem> VOID_LEGGINGS = armor("void_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorItem> VOID_BOOTS = armor("void_boots", ArmorItem.Type.BOOTS);

    public static final DeferredItem<SwordItem> EMISSARY_GREATSWORD = ITEMS.register("emissary_greatsword",
            () -> new SwordItem(ModTiers.VOID, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModTiers.VOID, 7, -3.0F))
                    .fireResistant().rarity(Rarity.EPIC)));

    public static final DeferredItem<VoidMaulItem> EXECUTIONER_MAUL = ITEMS.register("executioner_maul",
            () -> new VoidMaulItem(ModTiers.VOID, new Item.Properties()
                    .attributes(SwordItem.createAttributes(ModTiers.VOID, 10, -3.3F))
                    .fireResistant().rarity(Rarity.EPIC)));

    public static final DeferredItem<EndCompassItem> END_COMPASS = ITEMS.register("end_compass",
            () -> new EndCompassItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredItem<DeferredSpawnEggItem> EMISSARY_SPAWN_EGG = ITEMS.register("unknown_emissary_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.UNKNOWN_EMISSARY, 0x14101A, 0xB040FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> EXECUTIONER_SPAWN_EGG = ITEMS.register("executioner_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.EXECUTIONER, 0x1A1416, 0xE040A0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FIRST_SOUL_DRAGON_SPAWN_EGG = ITEMS.register("first_soul_dragon_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FIRST_SOUL_DRAGON, 0x0E0A12, 0x9D4EDD, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_SENTINEL_SPAWN_EGG = ITEMS.register("void_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_SENTINEL, 0x1A1620, 0x8A2BE2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_CAPTAIN_SPAWN_EGG = ITEMS.register("void_captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_CAPTAIN, 0x140E1C, 0xE0B040, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_FORGER_SPAWN_EGG = ITEMS.register("void_forger_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_FORGER, 0x6A6460, 0x8A2BE2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_GENERAL_SPAWN_EGG = ITEMS.register("void_general_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_GENERAL, 0x221E28, 0xD040FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_KING_SPAWN_EGG = ITEMS.register("void_king_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_KING, 0x2A0E3E, 0xF0C040, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_MAGE_SPAWN_EGG = ITEMS.register("void_mage_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_MAGE, 0x3A0E52, 0xC8A0FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_ARCHER_SPAWN_EGG = ITEMS.register("void_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_ARCHER, 0x1A1620, 0xD08CFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_EYE_SPAWN_EGG = ITEMS.register("void_eye_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VOID_EYE, 0x0D0014, 0xB05CFF, new Item.Properties()));
    /** Trofeo del Observador. */
    public static final DeferredItem<Item> OBSERVER_IRIS = ITEMS.register("observer_iris",
            () -> new Item(new Item.Properties().stacksTo(16).fireResistant().rarity(Rarity.EPIC)
                    .component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final DeferredItem<DeferredSpawnEggItem> CURSED_CREEPER_SPAWN_EGG = ITEMS.register("cursed_creeper_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CURSED_CREEPER, 0x0DA70B, 0xD01818, new Item.Properties()));

    private static DeferredItem<ArmorItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ArmorItem(ModArmorMaterials.VOID, type,
                new Item.Properties().durability(type.getDurability(40)).fireResistant().rarity(Rarity.EPIC)));
    }

    private ModItems() {}

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(VOID_HELMET);
            event.accept(VOID_CHESTPLATE);
            event.accept(VOID_LEGGINGS);
            event.accept(VOID_BOOTS);
            event.accept(EMISSARY_GREATSWORD);
            event.accept(EXECUTIONER_MAUL);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(END_COMPASS);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(OBSERVER_IRIS);
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(VOID_BLOCK);
            event.accept(VOID_STONE);
            event.accept(BLACK_ROCK);
            event.accept(BLACK_ROCK_BRICKS);
            event.accept(CRACKED_BLACK_ROCK_BRICKS);
            event.accept(POLISHED_BLACK_ROCK);
            event.accept(CHISELED_BLACK_ROCK_BRICKS);
            event.accept(BLACK_ROCK_PILLAR);
            event.accept(BLACK_ROCK_BRICK_STAIRS);
            event.accept(BLACK_ROCK_BRICK_SLAB);
            event.accept(BLACK_ROCK_BRICK_WALL);
            event.accept(VOID_CHAIN);
            event.accept(VOID_GLASS_PANE);
            event.accept(VOID_LANTERN);
            event.accept(OBELISK_CORE);
            event.accept(CHARRED_LOG);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(DEAD_GRASS_BLOCK);
            event.accept(BARREN_DIRT);
            event.accept(DEAD_GRASS);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(EMISSARY_SPAWN_EGG);
            event.accept(EXECUTIONER_SPAWN_EGG);
            event.accept(FIRST_SOUL_DRAGON_SPAWN_EGG);
            event.accept(VOID_SENTINEL_SPAWN_EGG);
            event.accept(VOID_ARCHER_SPAWN_EGG);
            event.accept(VOID_CAPTAIN_SPAWN_EGG);
            event.accept(VOID_FORGER_SPAWN_EGG);
            event.accept(VOID_GENERAL_SPAWN_EGG);
            event.accept(VOID_KING_SPAWN_EGG);
            event.accept(VOID_MAGE_SPAWN_EGG);
            event.accept(VOID_EYE_SPAWN_EGG);
            event.accept(CURSED_CREEPER_SPAWN_EGG);
        }
    }
}

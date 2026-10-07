package com.agustin.bloodmoon.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public final class ModTiers {
    /** Armas del Vacío: más duraderas y fuertes que netherite. Se reparan con fragmentos de eco. */
    public static final Tier VOID = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2500, 9.0F, 5.0F, 18, () -> Ingredient.of(Items.ECHO_SHARD));

    private ModTiers() {}
}

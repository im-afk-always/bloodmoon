package com.agustin.bloodmoon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Moneda de la Humanidad: cobre (1), plata (10), oro (100). Se cambian hacia abajo en la mesa y hacia arriba con un mercader. */
public class CoinItem extends Item {
    public final int value;

    public CoinItem(int value, Properties props) {
        super(props);
        this.value = value;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bloodmoon.coin.value", value * stack.getCount()).withStyle(ChatFormatting.GRAY));
    }
}

package com.agustin.bloodmoon.item;

import com.agustin.bloodmoon.world.ColiseumSites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Compás hacia el fin del mundo: clic derecho para sintonizarlo con el Coliseo del Vacío más cercano.
 * La aguja usa la misma lógica que la brújula vanilla (propiedad "angle").
 */
public class EndCompassItem extends Item {
    public EndCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);

        player.getCooldowns().addCooldown(this, 100);
        if (level.dimension() != Level.OVERWORLD) {
            player.displayClientMessage(Component.translatable("item.bloodmoon.end_compass.wrong_dimension")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        BlockPos found = ColiseumSites.nearest(sl, player.blockPosition(), 6).map(ColiseumSites.Site::center).orElse(null);
        if (found == null) {
            player.displayClientMessage(Component.translatable("item.bloodmoon.end_compass.none")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), found)), false));
        int dist = (int) Math.sqrt(player.blockPosition().distSqr(new BlockPos(found.getX(), player.getBlockY(), found.getZ())));
        player.displayClientMessage(Component.translatable("item.bloodmoon.end_compass.found", dist)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1F, 0.6F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER);
    }
}

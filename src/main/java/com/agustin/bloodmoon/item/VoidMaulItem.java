package com.agustin.bloodmoon.item;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.world.VoidImpact;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mazo del Ejecutor. Mantener clic derecho: alza el mazo, la cabeza se carga de energía;
 * soltar tras 1,5 s: golpe especial (cráter, Piedra del Vacío, llamas astrales y explosión).
 */
public class VoidMaulItem extends SwordItem {
    private static final int CHARGE_TICKS = 30;
    private static final int COOLDOWN_TICKS = 600;

    public VoidMaulItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int charged = getUseDuration(stack, entity) - remaining;
        if (level instanceof ServerLevel sl && charged % 4 == 0) {
            Vec3 head = entity.getEyePosition().add(0, 0.8, 0);
            sl.sendParticles(charged >= CHARGE_TICKS ? ParticleTypes.END_ROD : ParticleTypes.WITCH,
                    head.x, head.y, head.z, charged >= CHARGE_TICKS ? 3 : 2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charged = getUseDuration(stack, entity) - timeLeft;
        if (charged < CHARGE_TICKS || !(entity instanceof Player player) || !(level instanceof ServerLevel sl)) return;

        Vec3 look = player.getLookAngle();
        Vec3 at = player.position().add(look.x * 2.5, 0, look.z * 2.5);
        VoidImpact.crater(sl, at, 2.5F, 4.5F, true);
        float power = BloodMoonConfig.MAUL_SPECIAL_POWER.get().floatValue();
        if (power > 0) {
            level.explode(player, at.x, at.y, at.z, power, Level.ExplosionInteraction.TNT);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.5F);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.hurtAndBreak(20, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}

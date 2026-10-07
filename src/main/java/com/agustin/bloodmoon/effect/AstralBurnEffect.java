package com.agustin.bloodmoon.effect;

import com.agustin.bloodmoon.network.AstralBurnPayload;
import com.agustin.bloodmoon.registry.ModDamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Quemadura astral: llamas púrpuras que hacen el doble de daño que el fuego normal (2 por segundo por nivel).
 * No la frena la Resistencia al fuego; el agua sí la apaga. Los clientes cercanos dibujan llamas púrpuras.
 */
public class AstralBurnEffect extends MobEffect {
    public AstralBurnEffect() {
        super(MobEffectCategory.HARMFUL, 0xB040FF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.isInWater()) return false; // el agua apaga las llamas
        if (!entity.level().isClientSide) {
            entity.hurt(ModDamageTypes.astralBurn(entity.level()), 2.0F * (amplifier + 1));
            sync(entity);
        }
        return true;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) sync(entity);
    }

    private static void sync(LivingEntity entity) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new AstralBurnPayload(entity.getId()));
    }
}

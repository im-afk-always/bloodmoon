package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.MobBuffs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Esqueletos nacidos en Luna de Sangre disparan 2 flechas por ataque.
 * La segunda usa el mismo método vanilla, así que hereda su dispersión aleatoria,
 * el tipo de flecha (stray = lentitud, bogged = veneno) y los encantamientos del arco.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonMixin {
    @Unique
    private boolean bloodmoon$firingExtra;

    @Inject(method = "performRangedAttack", at = @At("TAIL"))
    private void bloodmoon$doubleShot(LivingEntity target, float velocity, CallbackInfo ci) {
        if (bloodmoon$firingExtra) return;
        AbstractSkeleton self = (AbstractSkeleton) (Object) this;
        if (self.level().isClientSide() || !MobBuffs.isBuffed(self)) return;

        bloodmoon$firingExtra = true;
        try {
            self.performRangedAttack(target, velocity);
        } finally {
            bloodmoon$firingExtra = false;
        }
    }
}

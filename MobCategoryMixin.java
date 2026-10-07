package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.BloodMoonManager;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Duplica el mob cap de MONSTER mientras se tickea el Overworld durante la Luna de Sangre.
 * Lo usan tanto el cap global como el cap local por jugador de NaturalSpawner.
 */
@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {
    @Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$doubleMonsterCap(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == MobCategory.MONSTER && BloodMoonManager.isSpawnBoostActive()) {
            cir.setReturnValue(cir.getReturnValue() * 2);
        }
    }
}

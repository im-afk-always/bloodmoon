package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.EclipseManager;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Durante la oscuridad del Eclipse Solar, el sol no quema a los no-muertos. require = 0: si no aplica, no crashea. */
@Mixin(Mob.class)
public abstract class MobSunBurnMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodmoon$eclipseShade(CallbackInfoReturnable<Boolean> cir) {
        if (EclipseManager.suppressSunBurn(((Mob) (Object) this).level())) cir.setReturnValue(false);
    }
}

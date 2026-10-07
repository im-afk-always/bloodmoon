package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.ClientBloodMoonState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cielo negro y sin estrellas durante la Luna de Sangre. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$hideStars(float partialTick, CallbackInfoReturnable<Float> cir) {
        float k = ClientBloodMoonState.intensity(partialTick);
        if (k > 0F) cir.setReturnValue(cir.getReturnValue() * (1F - k));
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$darkSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float k = ClientBloodMoonState.intensity(partialTick);
        if (k > 0F) {
            Vec3 c = cir.getReturnValue();
            Vec3 dark = new Vec3(0.015, 0.0, 0.0);
            cir.setReturnValue(c.lerp(dark, k));
        }
    }
}

package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Color de cielo y estrellas según el tipo de luna. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$stars(float partialTick, CallbackInfoReturnable<Float> cir) {
        MoonType type = ClientMoonState.visual();
        float k = ClientMoonState.intensity(partialTick);
        if (type != MoonType.NONE && k > 0F) {
            cir.setReturnValue(cir.getReturnValue() * (1F - k * (1F - type.starFactor)));
        }
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$sky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        MoonType type = ClientMoonState.visual();
        float k = ClientMoonState.intensity(partialTick);
        if (type != MoonType.NONE && k > 0F) {
            cir.setReturnValue(cir.getReturnValue().lerp(new Vec3(type.skyR, type.skyG, type.skyB), k));
        }
    }
}

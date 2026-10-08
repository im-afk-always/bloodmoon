package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.client.BloodLight;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tinte de la iluminación durante las lunas especiales: intercepta cada píxel del lightmap antes de subirlo a la GPU.
 * require = 0: si otra versión o mod cambia este código, el juego no crashea; simplemente no hay tinte.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @ModifyArg(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"),
            index = 2,
            require = 0)
    private int bloodmoon$tintLight(int block, int sky, int abgr) {
        return BloodLight.tint(block, sky, abgr);
    }
}

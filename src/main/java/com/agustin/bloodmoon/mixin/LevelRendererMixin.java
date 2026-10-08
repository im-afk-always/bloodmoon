package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.client.MoonTextures;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reemplaza la textura de la luna por la versión teñida (roja o dorada), y durante la Luna de Sangre oculta las nubes
 * cúbicas vanilla (BloodSkyRenderer dibuja nubes suaves propias).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyArg(
            method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"),
            index = 1)
    private ResourceLocation bloodmoon$tintedMoon(ResourceLocation texture) {
        if (MoonTextures.VANILLA_MOON.equals(texture)) {
            ResourceLocation tinted = MoonTextures.currentMoon();
            if (tinted != null) return tinted;
        }
        return texture;
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void bloodmoon$hideClouds(CallbackInfo ci) {
        if (com.agustin.bloodmoon.client.BloodSkyRenderer.hidesVanillaClouds()) ci.cancel();
    }
}

package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.client.MoonTextures;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Reemplaza la textura de la luna por la versión teñida (roja o dorada). */
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
}

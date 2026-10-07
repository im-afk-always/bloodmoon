package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientBloodMoonState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Cambia la textura de la luna por la luna roja mientras dura la Luna de Sangre. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique
    private static final ResourceLocation BLOODMOON$VANILLA_MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    @Unique
    private static final ResourceLocation BLOODMOON$RED_MOON =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "textures/environment/blood_moon_phases.png");

    @ModifyArg(
            method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"),
            index = 1)
    private ResourceLocation bloodmoon$redMoon(ResourceLocation texture) {
        if (ClientBloodMoonState.isRedMoon() && BLOODMOON$VANILLA_MOON.equals(texture)) {
            return BLOODMOON$RED_MOON;
        }
        return texture;
    }
}

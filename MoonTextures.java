package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.resources.ResourceLocation;

/** Luna vanilla teñida según el tipo de luna activo. */
public final class MoonTextures {
    public static final ResourceLocation VANILLA_MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    public static final ResourceLocation VANILLA_CREEPER_SWIRL =
            ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static final ResourceLocation RED_SWIRL =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/cursed_creeper_armor");

    private MoonTextures() {}

    /** Textura que reemplaza a la luna vanilla, o null si no hay luna especial. */
    public static ResourceLocation currentMoon() {
        if (!ClientMoonState.moonSwapped()) return null;
        MoonType type = ClientMoonState.visual();
        ResourceLocation target = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/moon_" + type.key());
        return TintedTextures.get(VANILLA_MOON, target, type.tintR, type.tintG, type.tintB, 1.15F);
    }

    public static ResourceLocation cursedSwirl() {
        return TintedTextures.get(VANILLA_CREEPER_SWIRL, RED_SWIRL, 255, 25, 20, 1.6F);
    }
}

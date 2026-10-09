package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.resources.ResourceLocation;

/** Luna vanilla teñida según el tipo de luna activo. */
public final class MoonTextures {
    public static final ResourceLocation VANILLA_MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    public static final ResourceLocation VANILLA_SUN =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    public static final ResourceLocation VANILLA_CREEPER_SWIRL =
            ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static final ResourceLocation RED_SWIRL =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/cursed_creeper_armor");

    private MoonTextures() {}

    /** Textura que reemplaza a la luna vanilla, o null si no hay luna especial. */
    public static ResourceLocation currentMoon() {
        if (!ClientMoonState.moonSwapped()) return null;
        MoonType type = ClientMoonState.visual();
        if (type.customSky()) {   // la luna realista la dibuja BloodSkyRenderer: la vanilla queda negra (invisible, se suma)
            return TintedTextures.get(VANILLA_MOON, ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/moon_hidden"), 0, 0, 0, 1F);
        }
        ResourceLocation target = ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/moon_" + type.key());
        return TintedTextures.get(VANILLA_MOON, target, type.tintR, type.tintG, type.tintB, 1.15F);
    }

    /** Sol vanilla en negro: se dibuja con mezcla aditiva, así que desaparece. */
    public static ResourceLocation hiddenSun() {
        return TintedTextures.get(VANILLA_SUN, ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dynamic/sun_hidden"), 0, 0, 0, 1F);
    }

    public static ResourceLocation cursedSwirl() {
        return TintedTextures.get(VANILLA_CREEPER_SWIRL, RED_SWIRL, 255, 25, 20, 1.6F);
    }
}

package com.agustin.bloodmoon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Buffs para mobs hostiles que aparecen durante la Luna de Sangre.
 * Todo se guarda en el NBT del mob (modificador permanente, efectos infinitos, tag persistente),
 * así que lo conserva hasta que muera o despawnee, aunque la Luna de Sangre termine.
 */
public final class MobBuffs {
    public static final String BUFFED_TAG = BloodMoonMod.MODID + ":buffed";
    private static final ResourceLocation FOLLOW_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "follow_range");

    private MobBuffs() {}

    public static boolean isBuffed(Entity entity) {
        return entity.getPersistentData().getBoolean(BUFFED_TAG);
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.loadedFromDisk()) return;                 // solo spawns nuevos
        if (level.dimension() != Level.OVERWORLD) return;
        if (!BloodMoonManager.isActive()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || !(entity instanceof Enemy)) return;
        if (isBuffed(mob)) return;

        mob.getPersistentData().putBoolean(BUFFED_TAG, true);

        // Rango de rastreo x2 (+100% del valor base)
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && !follow.hasModifier(FOLLOW_RANGE_ID)) {
            follow.addPermanentModifier(new AttributeModifier(
                    FOLLOW_RANGE_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        // Arañas (incluye arañas de cueva): Velocidad I
        if (mob instanceof Spider) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    MobEffectInstance.INFINITE_DURATION, 0, false, true));
        }

        // Zombis (incluye husk, drowned, aldeano zombi, piglin zombificado): Fuerza I
        if (mob instanceof Zombie) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                    MobEffectInstance.INFINITE_DURATION, 0, false, true));
        }

        // Esqueletos (AbstractSkeleton): el doble disparo lo hace AbstractSkeletonMixin leyendo BUFFED_TAG.
    }
}

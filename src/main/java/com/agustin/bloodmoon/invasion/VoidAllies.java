package com.agustin.bloodmoon.invasion;

import com.agustin.bloodmoon.entity.FirstSoulDragon;
import com.agustin.bloodmoon.entity.VoidKnight;
import com.agustin.bloodmoon.entity.VoidSkeleton;
import com.agustin.bloodmoon.registry.ModDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * El bando del Vacío no se hiere entre sí (flechas, barridos y explosiones de aliados) ni se quema con el fuego astral.
 */
public final class VoidAllies {
    private VoidAllies() {}

    public static boolean isVoid(Entity e) {
        return e instanceof VoidSkeleton || e instanceof VoidKnight || e instanceof FirstSoulDragon
                || e instanceof com.agustin.bloodmoon.entity.VoidGeneral || e instanceof com.agustin.bloodmoon.entity.VoidKing
                || e instanceof com.agustin.bloodmoon.entity.VoidMage
                || e instanceof net.minecraft.world.entity.monster.Vex v && v.getOwner() instanceof com.agustin.bloodmoon.entity.VoidMage;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!isVoid(victim)) return;
        DamageSource src = event.getSource();
        if (src.is(ModDamageTypes.ASTRAL_BURN) || src.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            return;
        }
        Entity attacker = src.getEntity(), direct = src.getDirectEntity();
        if (attacker != null && attacker != victim && isVoid(attacker) || direct != null && direct != victim && isVoid(direct)) {
            event.setCanceled(true);
        }
    }
}

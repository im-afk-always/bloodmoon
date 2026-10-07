package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.entity.CursedCreeper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** El Cursed Creeper explota dejando fuego. */
@Mixin(Creeper.class)
public abstract class CreeperMixin {
    @Redirect(method = "explodeCreeper", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion bloodmoon$cursedFire(Level level, Entity source, double x, double y, double z, float radius,
                                           Level.ExplosionInteraction interaction) {
        boolean fire = (Object) this instanceof CursedCreeper && BloodMoonConfig.CURSED_FIRE.get();
        return level.explode(source, x, y, z, radius, fire, interaction);
    }
}

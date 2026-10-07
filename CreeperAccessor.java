package com.agustin.bloodmoon.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("explosionRadius")
    int bloodmoon$getExplosionRadius();

    @Accessor("explosionRadius")
    void bloodmoon$setExplosionRadius(int radius);
}

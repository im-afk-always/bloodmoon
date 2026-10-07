package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.MobBuffs;
import com.agustin.bloodmoon.entity.ApocalypseRider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * - Esqueletos nacidos en Luna de Sangre: 2 flechas por ataque (repite el disparo vanilla).
 * - Jinete del Apocalipsis: 5 flechas en abanico horizontal.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonMixin {
    @Unique
    private static final float[] BLOODMOON$FAN_DEGREES = {-24F, -12F, 12F, 24F};

    @Unique
    private boolean bloodmoon$firingExtra;

    @Inject(method = "performRangedAttack", at = @At("TAIL"))
    private void bloodmoon$doubleShot(LivingEntity target, float velocity, CallbackInfo ci) {
        if (bloodmoon$firingExtra) return;
        AbstractSkeleton self = (AbstractSkeleton) (Object) this;
        if (self instanceof ApocalypseRider) return;
        if (self.level().isClientSide() || !MobBuffs.isBuffed(self)) return;

        bloodmoon$firingExtra = true;
        try {
            self.performRangedAttack(target, velocity);
        } finally {
            bloodmoon$firingExtra = false;
        }
    }

    @Redirect(method = "performRangedAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean bloodmoon$fanShot(Level level, Entity projectile) {
        boolean added = level.addFreshEntity(projectile);
        if (!added || !((Object) this instanceof ApocalypseRider) || !(projectile instanceof AbstractArrow arrow)) {
            return added;
        }

        CompoundTag snapshot = arrow.saveWithoutId(new CompoundTag());
        Vec3 motion = arrow.getDeltaMovement();
        for (float degrees : BLOODMOON$FAN_DEGREES) {
            Entity copy = arrow.getType().create(level);
            if (copy == null) continue;
            copy.load(snapshot);
            copy.setUUID(Mth.createInsecureUUID(level.random));

            Vec3 v = motion.yRot(degrees * Mth.DEG_TO_RAD);
            copy.setDeltaMovement(v);
            double horizontal = v.horizontalDistance();
            copy.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
            copy.setXRot((float) (Mth.atan2(v.y, horizontal) * Mth.RAD_TO_DEG));
            copy.yRotO = copy.getYRot();
            copy.xRotO = copy.getXRot();
            level.addFreshEntity(copy);
        }
        return true;
    }
}

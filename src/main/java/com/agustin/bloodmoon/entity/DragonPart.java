package com.agustin.bloodmoon.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/** Hitbox de una parte del Dragón de la Primera Alma (cabeza, cuello, torso, cola, alas). */
public class DragonPart extends PartEntity<FirstSoulDragon> {
    public final String name;
    /** Tamaño en px de modelo; se multiplica por los bloques por px del dragón. */
    private final float baseWidth, baseHeight;
    /** Multiplicador del daño recibido en esta parte. */
    public final float damageMultiplier;
    private EntityDimensions size;

    public DragonPart(FirstSoulDragon parent, String name, float width, float height, float damageMultiplier) {
        super(parent);
        this.name = name;
        this.baseWidth = width;
        this.baseHeight = height;
        this.damageMultiplier = damageMultiplier;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    void rescale(float unit) {
        this.size = EntityDimensions.scalable(baseWidth * unit, baseHeight * unit);
        this.refreshDimensions();
    }

    public float halfHeight() {
        return size.height() / 2F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }
}

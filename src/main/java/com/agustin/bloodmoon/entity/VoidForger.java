package com.agustin.bloodmoon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Forjador del Vacío: un esqueleto encorvado de hueso ceniciento con un mazo. Levanta las obras del Dominio bloque a
 * bloque. Si lo matás, la obra se detiene.
 */
public class VoidForger extends VoidSkeleton {
    /** Obra en la que trabaja (null = ninguna). */
    private BlockPos workSite;

    public VoidForger(EntityType<? extends AbstractSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return VoidSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.SCALE, 0.9);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
        setDropChance(EquipmentSlot.MAINHAND, 0F);
    }

    @Override
    public boolean isArcher() {
        return false;
    }

    public void setWorkSite(BlockPos site) {
        this.workSite = site;
    }

    public BlockPos getWorkSite() {
        return workSite;
    }

    /** Golpe de mazo sobre el bloque recién colocado. */
    public void hammer(BlockPos at) {
        getLookControl().setLookAt(Vec3.atCenterOf(at));
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (distanceToSqr(Vec3.atCenterOf(at)) > 36) getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // sin pelea, no se aleja de la obra
        if (!level().isClientSide && workSite != null && getTarget() == null && tickCount % 40 == 0
                && distanceToSqr(Vec3.atCenterOf(workSite)) > 100) {
            getNavigation().moveTo(workSite.getX() + 0.5, workSite.getY(), workSite.getZ() + 0.5, 1.0);
        }
    }
}

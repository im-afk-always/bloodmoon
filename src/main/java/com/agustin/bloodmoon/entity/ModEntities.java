package com.agustin.bloodmoon.entity;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.BloodMoonMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BloodMoonMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CursedCreeper>> CURSED_CREEPER =
            ENTITIES.register("cursed_creeper", () -> EntityType.Builder
                    .<CursedCreeper>of(CursedCreeper::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .clientTrackingRange(10)
                    .build("cursed_creeper"));

    public static final DeferredHolder<EntityType<?>, EntityType<ApocalypseRider>> APOCALYPSE_RIDER =
            ENTITIES.register("apocalypse_rider", () -> EntityType.Builder
                    .<ApocalypseRider>of(ApocalypseRider::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("apocalypse_rider"));

    private ModEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CURSED_CREEPER.get(), Creeper.createAttributes().build());
        event.put(APOCALYPSE_RIDER.get(), AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.SCALE, 2.0)
                .build());
    }

    /** La vida configurable se aplica al spawnear (el registro de atributos ocurre antes de leer la config). */
    public static double riderHealth() {
        return BloodMoonConfig.RIDER_HEALTH.get();
    }
}

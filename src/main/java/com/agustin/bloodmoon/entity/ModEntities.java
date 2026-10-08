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

    public static final DeferredHolder<EntityType<?>, EntityType<UnknownEmissary>> UNKNOWN_EMISSARY =
            ENTITIES.register("unknown_emissary", () -> EntityType.Builder
                    .<UnknownEmissary>of(UnknownEmissary::new, MobCategory.MONSTER)
                    .sized(3.0F, 12.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("unknown_emissary"));

    public static final DeferredHolder<EntityType<?>, EntityType<Executioner>> EXECUTIONER =
            ENTITIES.register("executioner", () -> EntityType.Builder
                    .<Executioner>of(Executioner::new, MobCategory.MONSTER)
                    .sized(3.2F, 12.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("executioner"));

    public static final DeferredHolder<EntityType<?>, EntityType<FirstSoulDragon>> FIRST_SOUL_DRAGON =
            ENTITIES.register("first_soul_dragon", () -> EntityType.Builder
                    .<FirstSoulDragon>of(FirstSoulDragon::new, MobCategory.MONSTER)
                    .sized(15.0F, 21.0F)
                    .fireImmune()
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .build("first_soul_dragon"));

    public static final DeferredHolder<EntityType<?>, EntityType<SoulCharge>> SOUL_CHARGE =
            ENTITIES.register("soul_charge", () -> EntityType.Builder
                    .<SoulCharge>of(SoulCharge::new, MobCategory.MISC)
                    .sized(1.5F, 1.5F)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .build("soul_charge"));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidSkeleton>> VOID_SENTINEL =
            ENTITIES.register("void_sentinel", () -> EntityType.Builder
                    .<VoidSkeleton>of(VoidSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("void_sentinel"));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidSkeleton>> VOID_ARCHER =
            ENTITIES.register("void_archer", () -> EntityType.Builder
                    .<VoidSkeleton>of(VoidSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("void_archer"));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidEye>> VOID_EYE =
            ENTITIES.register("void_eye", () -> EntityType.Builder
                    .<VoidEye>of(VoidEye::new, MobCategory.MONSTER)
                    .sized(18.0F, 18.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("void_eye"));

    public static final DeferredHolder<EntityType<?>, EntityType<EyeTentacle>> EYE_TENTACLE =
            ENTITIES.register("eye_tentacle", () -> EntityType.Builder
                    .<EyeTentacle>of(EyeTentacle::new, MobCategory.MONSTER)
                    .sized(1.4F, 7.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("eye_tentacle"));

    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEye>> WATCHER_EYE =
            ENTITIES.register("watcher_eye", () -> EntityType.Builder
                    .<WatcherEye>of(WatcherEye::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("watcher_eye"));

    private ModEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CURSED_CREEPER.get(), Creeper.createAttributes().build());
        event.put(UNKNOWN_EMISSARY.get(), UnknownEmissary.createAttributes().build());
        event.put(EXECUTIONER.get(), Executioner.createAttributes().build());
        event.put(FIRST_SOUL_DRAGON.get(), FirstSoulDragon.createAttributes().build());
        event.put(VOID_SENTINEL.get(), VoidSkeleton.createAttributes().build());
        event.put(VOID_ARCHER.get(), VoidSkeleton.createAttributes().build());
        event.put(VOID_EYE.get(), VoidEye.createAttributes().build());
        event.put(EYE_TENTACLE.get(), EyeTentacle.createAttributes().build());
        event.put(APOCALYPSE_RIDER.get(), AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.SCALE, 2.0)
                .build());
    }

    /** Spawn natural (bioma del Laberinto): en el piso, en la oscuridad, como cualquier monstruo. */
    public static void registerSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        for (EntityType<VoidSkeleton> type : java.util.List.of(VOID_SENTINEL.get(), VOID_ARCHER.get())) {
            event.register(type, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules,
                    net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    /** La vida configurable se aplica al spawnear (el registro de atributos ocurre antes de leer la config). */
    public static double riderHealth() {
        return BloodMoonConfig.RIDER_HEALTH.get();
    }
}

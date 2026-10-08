package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.network.BloodMoonNetwork;
import com.agustin.bloodmoon.registry.ModArmorMaterials;
import com.agustin.bloodmoon.registry.ModBlocks;
import com.agustin.bloodmoon.registry.ModEffects;
import com.agustin.bloodmoon.registry.ModItems;
import com.agustin.bloodmoon.registry.ModStructures;
import com.agustin.bloodmoon.registry.ModParticles;
import com.agustin.bloodmoon.registry.ModSounds;
import com.agustin.bloodmoon.registry.ModFeatures;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.world.VoidPortals;
import com.agustin.bloodmoon.world.ColiseumSites;
import com.agustin.bloodmoon.world.SupernovaCrater;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(BloodMoonMod.MODID)
public class BloodMoonMod {
    public static final String MODID = "bloodmoon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BloodMoonMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BloodMoonConfig.SPEC);

        ModEntities.ENTITIES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModStructures.STRUCTURE_TYPES.register(modBus);
        ModStructures.PIECE_TYPES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModDimensions.CHUNK_GENERATORS.register(modBus);
        modBus.addListener(ModItems::addToTabs);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);
        modBus.addListener(BloodMoonNetwork::register);

        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onPlayerChangeDimension);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MobBuffs::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(MobBuffs::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(BloodMoonCommand::register);
        NeoForge.EVENT_BUS.addListener(SupernovaCrater::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SupernovaCrater::onServerStopped);
        NeoForge.EVENT_BUS.addListener(VoidPortals::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.EyeSanctums::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.BeyondRift::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.BeyondRift::onDrops);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.BeyondRift::onRespawn);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.BeyondHoles::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.SurfaceBlast::onLevelTick);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.SurfaceBlast::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.agustin.bloodmoon.world.BeyondHoles::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SmokeTest::onServerStarted);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> ColiseumSites.clear());
    }
}

package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.network.BloodMoonNetwork;
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
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(BloodMoonNetwork::register);

        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onPlayerChangeDimension);
        NeoForge.EVENT_BUS.addListener(BloodMoonManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MobBuffs::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(MobBuffs::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(BloodMoonCommand::register);
    }
}

package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.BloodMoonMod;
import com.agustin.bloodmoon.ClientAstralState;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.registry.ModItems;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Solo cliente: renderers, fundido del cielo y color de niebla/horizonte. */
@Mod(value = BloodMoonMod.MODID, dist = Dist.CLIENT)
public class BloodMoonClient {

    public BloodMoonClient(IEventBus modBus) {
        modBus.addListener(BloodMoonClient::onRegisterRenderers);
        modBus.addListener(BloodMoonClient::onRegisterReloadListeners);
        modBus.addListener(BloodMoonClient::onRegisterLayers);
        modBus.addListener(BloodMoonClient::onRegisterGuiLayers);
        modBus.addListener(BloodMoonClient::onClientSetup);
        modBus.addListener(VoidArmorModels::onRegisterLayers);
        modBus.addListener(VoidArmorModels::onRegisterClientExtensions);
        modBus.addListener(BloodMoonClient::onAddLayers);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent e) -> e.register(
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "void_labyrinth"), new LabyrinthSky()));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent e) -> e.register(
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "beyond"), new BeyondSky()));
        modBus.addListener(BloodSkyRenderer::onRegisterShaders);
        modBus.addListener(NukeClouds::onRegisterProviders);
        NukeClouds.init();
        modBus.addListener(SupernovaFx::onRegisterProviders);
        modBus.addListener(DevotionAura::onRegisterProviders);
        SupernovaFx.init();
        IntroEye.init();
        EyeFightFx.init();

        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(DevotionAura::onClientTick);
        NeoForge.EVENT_BUS.addListener(ExploredMap::onChunkLoad);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ExploredMap.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ExploredMap.load());
        com.agustin.bloodmoon.ClientDevotion.onAurasChanged = () -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) level.players().forEach(net.minecraft.world.entity.player.Player::refreshDisplayName);
        };
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onFogColor);
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onLogout);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e) -> {
            if (e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen inv) {
                e.addListener(new DevotionButton(inv));
                e.addListener(new DominionMapButton(inv));
            }
        });
        NeoForge.EVENT_BUS.addListener(MoonlessSkyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(BloodSkyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(EclipseSkyRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onRenderFog);
        NeoForge.EVENT_BUS.addListener(BloodMoonClient::onSelectMusic);
        NeoForge.EVENT_BUS.addListener(MoonPostEffect::onRenderStage);
        NeoForge.EVENT_BUS.addListener(ModBossBars::onBossBar);
        NeoForge.EVENT_BUS.addListener(AstralFlameRenderer::onRenderLiving);
        NeoForge.EVENT_BUS.addListener(NukeClouds::onFogColor);
        NeoForge.EVENT_BUS.addListener(NukeClouds::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(SupernovaFx::onFogColor);
        NeoForge.EVENT_BUS.addListener(SupernovaFx::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(EyeFightFx::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(EyeFightFx::onFov);
        NeoForge.EVENT_BUS.addListener(EyeFightFx::onFogColor);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CURSED_CREEPER.get(), CursedCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.APOCALYPSE_RIDER.get(), WitherSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.UNKNOWN_EMISSARY.get(), EmissaryRenderer::new);
        event.registerEntityRenderer(ModEntities.EXECUTIONER.get(), ExecutionerRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRST_SOUL_DRAGON.get(), FirstSoulDragonRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_CHARGE.get(), SoulChargeRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_SENTINEL.get(), VoidSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_ARCHER.get(), VoidSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_CAPTAIN.get(), VoidSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_FORGER.get(), VoidSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_GENERAL.get(), VoidGeneralRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_EYE.get(), VoidEyeRenderer::new);
        event.registerEntityRenderer(ModEntities.EYE_TENTACLE.get(), EyeTentacleRenderer::new);
        event.registerEntityRenderer(ModEntities.WATCHER_EYE.get(), WatcherEyeRenderer::new);
        event.registerEntityRenderer(ModEntities.UNBOUND_OBSERVER.get(), VoidEyeRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_FIST.get(), VoidFistRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_PALM.get(), VoidPalmRenderer::new);
        event.registerEntityRenderer(ModEntities.COLOSSAL_EYE.get(), ColossalEyeRenderer::new);
        event.registerEntityRenderer(ModEntities.TITAN_TENTACLE.get(), TitanTentacleRenderer::new);
        event.registerEntityRenderer(ModEntities.ABYSS_TEAR.get(), AbyssTearRenderer::new);
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VoidGeneralModel.LAYER, VoidGeneralModel::createBodyLayer);
        event.registerLayerDefinition(EmissaryModel.LAYER, EmissaryModel::createBodyLayer);
        event.registerLayerDefinition(ExecutionerModel.LAYER, ExecutionerModel::createBodyLayer);
        event.registerLayerDefinition(FirstSoulDragonModel.LAYER, FirstSoulDragonModel::createBodyLayer);
    }

    /** Capa emisiva del Set del Vacío en jugadores y soportes de armadura. */
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new VoidArmorGlowLayer<>(renderer));
        }
        if (event.getRenderer(EntityType.ARMOR_STAND) instanceof ArmorStandRenderer renderer) {
            renderer.addLayer(new VoidArmorGlowLayer<>(renderer));
        }
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "astral_burn"), AstralFlameRenderer::renderOverlay);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eclipse_flash"), EclipseSkyRenderer::renderFlash);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY,
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "offering"), OfferingHud::render);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "nuke_flash"), NukeClouds::renderFlash);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eye_madness"), EyeFightFx::renderMadness);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "supernova"), SupernovaFx::renderOverlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eye_title"), EyeFightFx::renderTitle);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "intro_eye"), IntroEye::render);
    }

    /** La aguja del Compás usa la misma propiedad "angle" que la brújula vanilla. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.END_COMPASS.get(), ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> {
                    LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                    return tracker != null ? tracker.target().orElse(null) : null;
                })));
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            TintedTextures.invalidate();
            VoidArmorModels.invalidate();
            MoonPostEffect.invalidate();
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) return;
        ClientMoonState.tick();
        tickHarvestMusic();
        ClientAstralState.tick();
        NukeClouds.tick();
        SupernovaFx.tick();
        IntroEye.tick();
        EyeFightFx.tick();
        EclipseSkyRenderer.tick();
    }

    /** El color de niebla es también el del horizonte: oscuro con el tinte de cada luna. */
    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        com.agustin.bloodmoon.Eclipse.State ec = EclipseSkyRenderer.state((float) event.getPartialTick());
        if (ec != null && ec.dark() > 0.001F) {
            // en el eclipse el horizonte queda iluminado todo alrededor: un atardecer de 360°, pardo y apagado
            float k = ec.dark();
            float glow = 0.55F + 0.45F * k;
            event.setRed(lerp(event.getRed(), 0.30F * glow, k));
            event.setGreen(lerp(event.getGreen(), 0.19F * glow, k));
            event.setBlue(lerp(event.getBlue(), 0.12F * glow, k));
            return;
        }
        MoonType type = ClientMoonState.visual();
        float k = ClientMoonState.intensity((float) event.getPartialTick());
        if (type == MoonType.NONE || k <= 0F) return;
        event.setRed(lerp(event.getRed(), type.fogR, k));
        event.setGreen(lerp(event.getGreen(), type.fogG, k));
        event.setBlue(lerp(event.getBlue(), type.fogB, k));
    }

    /** Durante la Luna de Sangre la bruma carmesí empieza más cerca: el horizonte se pierde en rojo. */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) return;
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        MoonType type = ClientMoonState.visual();
        if (!type.customSky()) return;
        float k = ClientMoonState.intensity((float) event.getPartialTick());
        if (k <= 0F) return;
        float near = lerp(event.getNearPlaneDistance(), event.getFarPlaneDistance() * (type == MoonType.GOLDEN ? 0.5F : 0.3F), k);
        if (near >= event.getNearPlaneDistance()) return;
        event.setNearPlaneDistance(near);
        event.setCanceled(true);
    }

    private static net.minecraft.sounds.Music beyondMusic, harvestMusic;
    private static boolean wasInBeyond, wasHarvest;

    private static boolean harvestNight(Minecraft mc) {
        return mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && ClientMoonState.visual() == MoonType.SUPER && ClientMoonState.intensity(1F) > 0.05F;
    }

    /**
     * En el Más Allá de la Grieta suena la música de la batalla final, en bucle. Durante la Luna de la Cosecha suena
     * el Lacrimosa, con una pausa de 30-60 s entre repeticiones; al amanecer termina de sonar sola.
     */
    private static void onSelectMusic(net.neoforged.neoforge.client.event.SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        boolean inBeyond = mc.level != null && mc.level.dimension() == com.agustin.bloodmoon.registry.ModDimensions.BEYOND;
        if (inBeyond) {
            if (beyondMusic == null) beyondMusic = new net.minecraft.sounds.Music(com.agustin.bloodmoon.registry.ModSounds.MUSIC_BEYOND, 0, 0, true);
            event.setMusic(beyondMusic);
        } else {
            if (wasInBeyond) mc.getMusicManager().stopPlaying();   // al volver, la pista de la batalla no sigue sonando en el mundo normal
            if (harvestNight(mc)) event.setMusic(harvestMusic());
        }
        wasInBeyond = inBeyond;
    }

    private static net.minecraft.sounds.Music harvestMusic() {
        if (harvestMusic == null) harvestMusic = new net.minecraft.sounds.Music(com.agustin.bloodmoon.registry.ModSounds.MUSIC_HARVEST, 600, 1200, true);
        return harvestMusic;
    }

    /** Cuando sale la Luna de la Cosecha, el Lacrimosa empieza enseguida (sin esperar el turno normal de la música). */
    private static void tickHarvestMusic() {
        Minecraft mc = Minecraft.getInstance();
        boolean harvest = harvestNight(mc);
        if (harvest && !wasHarvest && !mc.getMusicManager().isPlayingMusic(harvestMusic())) {
            mc.getMusicManager().stopPlaying();
            mc.getMusicManager().startPlaying(harvestMusic());
        }
        wasHarvest = harvest;
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMoonState.reset();
        com.agustin.bloodmoon.ClientEclipse.reset();
        com.agustin.bloodmoon.ClientOffering.reset();
        com.agustin.bloodmoon.ClientDevotion.reset();
        com.agustin.bloodmoon.ClientDominion.reset();
        ExploredMap.save();
        ClientAstralState.reset();
        NukeClouds.reset();
        SupernovaFx.reset();
        IntroEye.reset();
        EyeFightFx.reset();
        TintedTextures.invalidate();
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}

package com.agustin.bloodmoon.client;

import com.agustin.bloodmoon.human.Human;
import com.agustin.bloodmoon.human.HumanSkin;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Humanos con el modelo de jugador (ancho o fino según sus rasgos) y una skin pintada al vuelo, cacheada. */
public class HumanRenderer extends HumanoidMobRenderer<Human, PlayerModel<Human>> {
    private final PlayerModel<Human> wide;
    private final PlayerModel<Human> slim;

    public HumanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(Human human, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        this.model = human.isSlim() ? slim : wide;
        HumanoidModel.ArmPose main = human.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose off = human.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        this.model.rightArmPose = main;
        this.model.leftArmPose = off;
        super.render(human, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void scale(Human human, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    public ResourceLocation getTextureLocation(Human human) {
        return Skins.get(human.seed(), human.job().ordinal(), human.culture().ordinal());
    }

    /** Cache LRU de texturas dinámicas: una por combinación semilla/oficio/cultura. */
    static final class Skins {
        private static final int MAX = 384;
        private static final Map<Long, ResourceLocation> CACHE = new LinkedHashMap<>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ResourceLocation> eldest) {
                if (size() <= MAX) return false;
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
        };

        static ResourceLocation get(int seed, int job, int culture) {
            long key = ((long) seed << 16) ^ ((long) job << 4) ^ culture;
            ResourceLocation rl = CACHE.get(key);
            if (rl != null) return rl;
            int[] argb = HumanSkin.paint(seed, com.agustin.bloodmoon.human.HumanJob.byId(job),
                    com.agustin.bloodmoon.human.Culture.byId(culture));
            NativeImage img = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int c = argb[y * 64 + x];
                    int abgr = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
                    img.setPixelRGBA(x, y, abgr);
                }
            }
            rl = ResourceLocation.fromNamespaceAndPath("bloodmoon", "human_skin/" + Long.toHexString(key));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            CACHE.put(key, rl);
            return rl;
        }
    }
}

package com.agustin.bloodmoon.client;

import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/** Creeper normal con el aura eléctrica en rojo en vez de celeste. */
public class CursedCreeperRenderer extends CreeperRenderer {
    public CursedCreeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.removeIf(layer -> layer instanceof CreeperPowerLayer);
        this.addLayer(new RedPowerLayer(this, context.getModelSet()));
    }

    static class RedPowerLayer extends CreeperPowerLayer {
        RedPowerLayer(RenderLayerParent<Creeper, CreeperModel<Creeper>> parent, EntityModelSet models) {
            super(parent, models);
        }

        @Override
        protected ResourceLocation getTextureLocation() {
            return MoonTextures.cursedSwirl();
        }
    }
}

package com.agustin.bloodmoon.mixin;

import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Color de cielo y estrellas según el tipo de luna. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$stars(float partialTick, CallbackInfoReturnable<Float> cir) {
        com.agustin.bloodmoon.Eclipse.State ec = bloodmoon$eclipse(partialTick);
        if (ec != null && ec.dark() > 0.3F) {   // en la totalidad aparecen las estrellas en pleno día
            float k = (ec.dark() - 0.3F) / 0.7F;
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.75F * k * k));
            return;
        }
        MoonType type = ClientMoonState.visual();
        float k = ClientMoonState.intensity(partialTick);
        if (type != MoonType.NONE && k > 0F) {
            cir.setReturnValue(cir.getReturnValue() * (1F - k * (1F - type.starFactor)));
        }
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void bloodmoon$sky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        com.agustin.bloodmoon.Eclipse.State ec = bloodmoon$eclipse(partialTick);
        if (ec != null && ec.dark() > 0.001F) {   // el cielo se apaga hacia un azul pizarra casi negro
            cir.setReturnValue(cir.getReturnValue().lerp(new Vec3(0.035, 0.04, 0.065), ec.dark()));
            return;
        }
        MoonType type = ClientMoonState.visual();
        float k = ClientMoonState.intensity(partialTick);
        if (type != MoonType.NONE && k > 0F) {
            cir.setReturnValue(cir.getReturnValue().lerp(new Vec3(type.skyR, type.skyG, type.skyB), k));
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void bloodmoon$clouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        com.agustin.bloodmoon.Eclipse.State ec = bloodmoon$eclipse(partialTick);
        if (ec != null && ec.dark() > 0.001F) {   // nubes grises y apagadas, con un dejo pardo
            cir.setReturnValue(cir.getReturnValue().multiply(0.62, 0.58, 0.54).lerp(new Vec3(0.09, 0.08, 0.075), ec.dark()));
        }
    }

    private com.agustin.bloodmoon.Eclipse.State bloodmoon$eclipse(float partialTick) {
        net.minecraft.client.multiplayer.ClientLevel self = (net.minecraft.client.multiplayer.ClientLevel) (Object) this;
        if (self.dimension() != net.minecraft.world.level.Level.OVERWORLD) return null;
        return com.agustin.bloodmoon.ClientEclipse.state(self.getDayTime(), partialTick);
    }
}

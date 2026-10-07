package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: esta entidad está ardiendo con llamas astrales (se refresca cada segundo). */
public record AstralBurnPayload(int entityId) implements CustomPacketPayload {
    public static final Type<AstralBurnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "astral_burn"));

    public static final StreamCodec<ByteBuf, AstralBurnPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AstralBurnPayload::entityId, AstralBurnPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

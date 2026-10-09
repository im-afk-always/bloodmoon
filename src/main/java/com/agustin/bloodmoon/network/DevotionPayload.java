package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: devoción del jugador ("" = ninguna) y su reputación (para el ícono del inventario y su pantalla). */
public record DevotionPayload(String deity, int reputation, int partial) implements CustomPacketPayload {
    public static final Type<DevotionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "devotion"));

    public static final StreamCodec<ByteBuf, DevotionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DevotionPayload::deity,
            ByteBufCodecs.VAR_INT, DevotionPayload::reputation,
            ByteBufCodecs.VAR_INT, DevotionPayload::partial,
            DevotionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

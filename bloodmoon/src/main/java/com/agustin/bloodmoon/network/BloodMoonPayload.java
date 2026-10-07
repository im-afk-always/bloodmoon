package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: tipo de luna activo en la dimensión del jugador (ordinal de MoonType). */
public record BloodMoonPayload(int moonType) implements CustomPacketPayload {
    public static final Type<BloodMoonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "state"));

    public static final StreamCodec<ByteBuf, BloodMoonPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, BloodMoonPayload::moonType, BloodMoonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

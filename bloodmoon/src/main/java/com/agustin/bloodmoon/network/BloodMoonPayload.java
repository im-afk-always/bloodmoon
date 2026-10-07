package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: ¿hay Luna de Sangre en la dimensión donde está el jugador? */
public record BloodMoonPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BloodMoonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "state"));

    public static final StreamCodec<ByteBuf, BloodMoonPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, BloodMoonPayload::active, BloodMoonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

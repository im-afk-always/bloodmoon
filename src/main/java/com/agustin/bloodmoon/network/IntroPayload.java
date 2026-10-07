package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: mostrar la visión del ojo (primera vez que el jugador entra al mundo). */
public record IntroPayload() implements CustomPacketPayload {
    public static final Type<IntroPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "intro"));
    public static final StreamCodec<ByteBuf, IntroPayload> STREAM_CODEC = StreamCodec.unit(new IntroPayload());

    public static Runnable handler = () -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

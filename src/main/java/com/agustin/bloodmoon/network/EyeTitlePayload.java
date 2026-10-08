package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Servidor -> cliente: cinemática de texto del Observador (despertar o despedida). */
public record EyeTitlePayload(int mode) implements CustomPacketPayload {
    public static final int AWAKEN = 0, FAREWELL = 1;

    public static final Type<EyeTitlePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eye_title"));

    public static final StreamCodec<ByteBuf, EyeTitlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EyeTitlePayload::mode,
            EyeTitlePayload::new);

    public static Consumer<EyeTitlePayload> handler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Servidor -> cliente: locura del jugador (0-100) frente al Observador. */
public record EyeMadnessPayload(int eyeId, float madness) implements CustomPacketPayload {
    public static final Type<EyeMadnessPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eye_madness"));

    public static final StreamCodec<ByteBuf, EyeMadnessPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EyeMadnessPayload::eyeId,
            ByteBufCodecs.FLOAT, EyeMadnessPayload::madness,
            EyeMadnessPayload::new);

    public static Consumer<EyeMadnessPayload> handler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

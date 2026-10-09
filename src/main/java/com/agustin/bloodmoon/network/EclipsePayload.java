package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: día del Eclipse Solar (-1 = ninguno, o el jugador no está en el Overworld). */
public record EclipsePayload(long day) implements CustomPacketPayload {
    public static final Type<EclipsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "eclipse"));

    public static final StreamCodec<ByteBuf, EclipsePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, EclipsePayload::day, EclipsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Servidor -> cliente: el Dragón de la Primera Alma murió y empieza la supernova.
 * (x, y, z) = alma del dragón al morir; groundY = suelo debajo; scale = tamaño del dragón.
 */
public record SupernovaPayload(double x, double y, double z, double groundY, float scale) implements CustomPacketPayload {
    public static final Type<SupernovaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "supernova"));

    public static final StreamCodec<ByteBuf, SupernovaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SupernovaPayload::x,
            ByteBufCodecs.DOUBLE, SupernovaPayload::y,
            ByteBufCodecs.DOUBLE, SupernovaPayload::z,
            ByteBufCodecs.DOUBLE, SupernovaPayload::groundY,
            ByteBufCodecs.FLOAT, SupernovaPayload::scale,
            SupernovaPayload::new);

    /** Tiempos de la secuencia (ticks desde la muerte); el servidor y el cliente usan los mismos. */
    public static final int RISE_END = 100, COLLAPSE_START = 104, DETONATE = 125;
    /** Ascenso por tick durante la secuencia. */
    public static final double RISE_SPEED = 0.3;

    public static Consumer<SupernovaPayload> handler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

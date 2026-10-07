package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Servidor -> cliente: una carga del dragón tocó el suelo; el cliente dibuja el hongo. mult = 3, 5 o 10. */
public record NukePayload(double x, double y, double z, byte mult) implements CustomPacketPayload {
    public static final Type<NukePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "nuke"));

    public static final StreamCodec<ByteBuf, NukePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, NukePayload::x,
            ByteBufCodecs.DOUBLE, NukePayload::y,
            ByteBufCodecs.DOUBLE, NukePayload::z,
            ByteBufCodecs.BYTE, NukePayload::mult,
            NukePayload::new);

    /** Lo reemplaza el cliente (así el servidor dedicado no carga clases de render). */
    public static Consumer<NukePayload> handler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

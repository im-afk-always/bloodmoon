package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Servidor -> cliente: la Palma del Vacío empezó a descender sobre (x, y, z) (mode 0) o se canceló (mode 1). */
public record PalmPayload(int mode, double x, double y, double z) implements CustomPacketPayload {
    public static final int START = 0, CANCEL = 1;

    public static final Type<PalmPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "palm"));

    public static final StreamCodec<ByteBuf, PalmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PalmPayload::mode,
            ByteBufCodecs.DOUBLE, PalmPayload::x,
            ByteBufCodecs.DOUBLE, PalmPayload::y,
            ByteBufCodecs.DOUBLE, PalmPayload::z,
            PalmPayload::new);

    public static Consumer<PalmPayload> handler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

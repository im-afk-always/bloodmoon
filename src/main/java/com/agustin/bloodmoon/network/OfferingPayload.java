package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: estado de la ofrenda de esta luna (para el contador de arriba a la derecha). */
public record OfferingPayload(boolean active, int done, int required, boolean unskippable, String deity) implements CustomPacketPayload {
    public static final Type<OfferingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "offering"));

    public static final StreamCodec<ByteBuf, OfferingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OfferingPayload::active,
            ByteBufCodecs.VAR_INT, OfferingPayload::done,
            ByteBufCodecs.VAR_INT, OfferingPayload::required,
            ByteBufCodecs.BOOL, OfferingPayload::unskippable,
            ByteBufCodecs.STRING_UTF8, OfferingPayload::deity,
            OfferingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

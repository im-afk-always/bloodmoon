package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: "mandame el mapa del Dominio". */
public record DominionMapRequestPayload() implements CustomPacketPayload {
    public static final DominionMapRequestPayload INSTANCE = new DominionMapRequestPayload();
    public static final Type<DominionMapRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dominion_map_request"));
    public static final StreamCodec<ByteBuf, DominionMapRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

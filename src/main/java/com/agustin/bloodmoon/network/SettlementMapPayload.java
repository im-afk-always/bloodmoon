package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Servidor -> cliente: los asentamientos humanos cercanos para el mapa (centro, radio de influencia, nivel, color). */
public record SettlementMapPayload(List<View> settlements) implements CustomPacketPayload {
    public static final Type<SettlementMapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "settlement_map"));

    public record View(String name, int x, int z, int radius, int level, int pop, int color, boolean port) {}

    public static final StreamCodec<ByteBuf, SettlementMapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.settlements().size());
                for (View v : p.settlements()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, v.name());
                    ByteBufCodecs.INT.encode(buf, v.x());
                    ByteBufCodecs.INT.encode(buf, v.z());
                    ByteBufCodecs.VAR_INT.encode(buf, v.radius());
                    ByteBufCodecs.VAR_INT.encode(buf, v.level());
                    ByteBufCodecs.VAR_INT.encode(buf, v.pop());
                    ByteBufCodecs.INT.encode(buf, v.color());
                    ByteBufCodecs.BOOL.encode(buf, v.port());
                }
            },
            buf -> {
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                List<View> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new View(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.INT.decode(buf), ByteBufCodecs.INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));
                }
                return new SettlementMapPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

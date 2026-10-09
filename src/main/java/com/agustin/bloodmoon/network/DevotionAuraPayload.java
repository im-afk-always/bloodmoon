package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Servidor -> todos: deidad y nivel de adoración de cada devoto (para dibujar su aura). */
public record DevotionAuraPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(UUID id, String deity, int level) {}

    public static final Type<DevotionAuraPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "devotion_aura"));

    public static final StreamCodec<ByteBuf, DevotionAuraPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.entries().size());
                for (Entry e : p.entries()) {
                    UUIDUtil.STREAM_CODEC.encode(buf, e.id());
                    ByteBufCodecs.STRING_UTF8.encode(buf, e.deity());
                    ByteBufCodecs.VAR_INT.encode(buf, e.level());
                }
            },
            buf -> {
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                    String deity = ByteBufCodecs.STRING_UTF8.decode(buf);
                    list.add(new Entry(id, deity, ByteBufCodecs.VAR_INT.decode(buf)));
                }
                return new DevotionAuraPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

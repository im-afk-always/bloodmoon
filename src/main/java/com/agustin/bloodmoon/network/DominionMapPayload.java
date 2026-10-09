package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.BloodMoonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor -> cliente: los Dominios cercanos para el mapa. La grilla tiene un byte por chunk alrededor del eje
 * (0 = libre, 1-100 = influencia, 101 = muerto con obelisco).
 */
public record DominionMapPayload(List<FactionView> factions) implements CustomPacketPayload {
    public static final Type<DominionMapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BloodMoonMod.MODID, "dominion_map"));

    public record RankView(int rank, String name, boolean alive) {}

    public record FactionView(int id, String name, int centerX, int centerZ, int radius, int phase, int essence, boolean active,
                              boolean healing, int forgers, int troops, int deadChunks, int obelisks, List<RankView> ranks,
                              int half, byte[] grid) {
        /** Valor de la grilla para un chunk (0 si cae afuera). */
        public int at(int chunkX, int chunkZ) {
            int gx = chunkX - (centerX >> 4) + half, gz = chunkZ - (centerZ >> 4) + half;
            int side = half * 2 + 1;
            if (gx < 0 || gz < 0 || gx >= side || gz >= side) return 0;
            return grid[gz * side + gx] & 0xFF;
        }
    }

    public static final StreamCodec<ByteBuf, DominionMapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.factions().size());
                for (FactionView f : p.factions()) {
                    ByteBufCodecs.VAR_INT.encode(buf, f.id());
                    ByteBufCodecs.STRING_UTF8.encode(buf, f.name());
                    ByteBufCodecs.VAR_INT.encode(buf, f.centerX());
                    ByteBufCodecs.VAR_INT.encode(buf, f.centerZ());
                    ByteBufCodecs.VAR_INT.encode(buf, f.radius());
                    ByteBufCodecs.VAR_INT.encode(buf, f.phase());
                    ByteBufCodecs.VAR_INT.encode(buf, f.essence());
                    ByteBufCodecs.BOOL.encode(buf, f.active());
                    ByteBufCodecs.BOOL.encode(buf, f.healing());
                    ByteBufCodecs.VAR_INT.encode(buf, f.forgers());
                    ByteBufCodecs.VAR_INT.encode(buf, f.troops());
                    ByteBufCodecs.VAR_INT.encode(buf, f.deadChunks());
                    ByteBufCodecs.VAR_INT.encode(buf, f.obelisks());
                    ByteBufCodecs.VAR_INT.encode(buf, f.ranks().size());
                    for (RankView r : f.ranks()) {
                        ByteBufCodecs.VAR_INT.encode(buf, r.rank());
                        ByteBufCodecs.STRING_UTF8.encode(buf, r.name());
                        ByteBufCodecs.BOOL.encode(buf, r.alive());
                    }
                    ByteBufCodecs.VAR_INT.encode(buf, f.half());
                    ByteBufCodecs.BYTE_ARRAY.encode(buf, f.grid());
                }
            },
            buf -> {
                int n = ByteBufCodecs.VAR_INT.decode(buf);
                List<FactionView> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int id = ByteBufCodecs.VAR_INT.decode(buf);
                    String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                    int cx = ByteBufCodecs.VAR_INT.decode(buf), cz = ByteBufCodecs.VAR_INT.decode(buf);
                    int radius = ByteBufCodecs.VAR_INT.decode(buf), phase = ByteBufCodecs.VAR_INT.decode(buf);
                    int essence = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean active = ByteBufCodecs.BOOL.decode(buf), healing = ByteBufCodecs.BOOL.decode(buf);
                    int forgers = ByteBufCodecs.VAR_INT.decode(buf), troops = ByteBufCodecs.VAR_INT.decode(buf);
                    int dead = ByteBufCodecs.VAR_INT.decode(buf), obelisks = ByteBufCodecs.VAR_INT.decode(buf);
                    int rn = ByteBufCodecs.VAR_INT.decode(buf);
                    List<RankView> ranks = new ArrayList<>(rn);
                    for (int k = 0; k < rn; k++) {
                        int rank = ByteBufCodecs.VAR_INT.decode(buf);
                        String rname = ByteBufCodecs.STRING_UTF8.decode(buf);
                        ranks.add(new RankView(rank, rname, ByteBufCodecs.BOOL.decode(buf)));
                    }
                    int half = ByteBufCodecs.VAR_INT.decode(buf);
                    byte[] grid = ByteBufCodecs.BYTE_ARRAY.decode(buf);
                    list.add(new FactionView(id, name, cx, cz, radius, phase, essence, active, healing, forgers, troops, dead, obelisks,
                            ranks, half, grid));
                }
                return new DominionMapPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

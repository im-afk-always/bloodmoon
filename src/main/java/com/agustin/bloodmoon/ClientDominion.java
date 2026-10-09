package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.DominionMapPayload;

import java.util.List;

/** Último mapa del Dominio recibido (sin imports de cliente: lo toca el handler del paquete). */
public final class ClientDominion {
    public static volatile List<DominionMapPayload.FactionView> factions = List.of();
    /** Sube con cada paquete: el mapa sabe cuándo redibujar. */
    public static volatile int version;

    private ClientDominion() {}

    public static void set(List<DominionMapPayload.FactionView> list) {
        factions = List.copyOf(list);
        version++;
    }

    public static void reset() {
        factions = List.of();
        version++;
    }
}

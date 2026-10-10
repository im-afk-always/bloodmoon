package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.SettlementMapPayload;

import java.util.List;

/** Últimos asentamientos humanos recibidos para el mapa. */
public final class ClientSettlements {
    public static volatile List<SettlementMapPayload.View> list = List.of();

    private ClientSettlements() {}

    public static void set(List<SettlementMapPayload.View> l) {
        list = List.copyOf(l);
    }
}

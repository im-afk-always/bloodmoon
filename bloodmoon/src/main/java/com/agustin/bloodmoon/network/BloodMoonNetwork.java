package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.ClientBloodMoonState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BloodMoonNetwork {
    private BloodMoonNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC,
                (payload, context) -> ClientBloodMoonState.setTarget(payload.active()));
    }
}

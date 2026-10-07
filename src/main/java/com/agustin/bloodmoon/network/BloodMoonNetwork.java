package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.ClientAstralState;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BloodMoonNetwork {
    private BloodMoonNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC,
                (payload, context) -> ClientMoonState.setTarget(MoonType.byId(payload.moonType())));
        registrar.playToClient(AstralBurnPayload.TYPE, AstralBurnPayload.STREAM_CODEC,
                (payload, context) -> ClientAstralState.mark(payload.entityId()));
    }
}

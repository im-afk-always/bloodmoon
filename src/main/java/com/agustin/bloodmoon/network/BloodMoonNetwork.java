package com.agustin.bloodmoon.network;

import com.agustin.bloodmoon.ClientAstralState;
import com.agustin.bloodmoon.ClientMoonState;
import com.agustin.bloodmoon.MoonType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BloodMoonNetwork {
    private BloodMoonNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("11");
        registrar.playToClient(BloodMoonPayload.TYPE, BloodMoonPayload.STREAM_CODEC,
                (payload, context) -> ClientMoonState.setTarget(MoonType.byId(payload.moonType())));
        registrar.playToClient(AstralBurnPayload.TYPE, AstralBurnPayload.STREAM_CODEC,
                (payload, context) -> ClientAstralState.mark(payload.entityId()));
        registrar.playToClient(NukePayload.TYPE, NukePayload.STREAM_CODEC,
                (payload, context) -> NukePayload.handler.accept(payload));
        registrar.playToClient(SupernovaPayload.TYPE, SupernovaPayload.STREAM_CODEC,
                (payload, context) -> SupernovaPayload.handler.accept(payload));
        registrar.playToClient(EyeMadnessPayload.TYPE, EyeMadnessPayload.STREAM_CODEC, (payload, context) -> EyeMadnessPayload.handler.accept(payload));
        registrar.playToClient(EyeTitlePayload.TYPE, EyeTitlePayload.STREAM_CODEC, (payload, context) -> EyeTitlePayload.handler.accept(payload));
        registrar.playToClient(PalmPayload.TYPE, PalmPayload.STREAM_CODEC, (payload, context) -> PalmPayload.handler.accept(payload));
        registrar.playToClient(EclipsePayload.TYPE, EclipsePayload.STREAM_CODEC,
                (payload, context) -> com.agustin.bloodmoon.ClientEclipse.set(payload.day()));
        registrar.playToClient(OfferingPayload.TYPE, OfferingPayload.STREAM_CODEC,
                (payload, context) -> com.agustin.bloodmoon.ClientOffering.set(payload.active(), payload.done(), payload.required(), payload.unskippable()));
        registrar.playToClient(DevotionPayload.TYPE, DevotionPayload.STREAM_CODEC,
                (payload, context) -> com.agustin.bloodmoon.ClientDevotion.set(payload.deity(), payload.reputation()));
        registrar.playToClient(IntroPayload.TYPE, IntroPayload.STREAM_CODEC, (payload, context) -> IntroPayload.handler.run());
    }
}

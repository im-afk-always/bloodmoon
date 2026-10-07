package com.agustin.bloodmoon.client;

import net.minecraft.util.Mth;

/** Interpolación suavizada entre claves (tiempo, valor, tiempo, valor, ...). */
final class KeyFrames {
    private KeyFrames() {}

    static float key(float t, float... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) {
                float a = (t - kv[i - 2]) / (kv[i] - kv[i - 2]);
                a = a * a * (3F - 2F * a);
                return Mth.lerp(a, kv[i - 1], kv[i + 1]);
            }
        }
        return kv[kv.length - 1];
    }
}

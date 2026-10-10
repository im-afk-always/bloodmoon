package com.agustin.bloodmoon.human;

/** Culturas humanas (por bioma). Deciden arquitectura, ropa, nombres y tonos de piel más frecuentes. */
public enum Culture {
    PLAINS,
    DESERT;

    public static Culture byId(int id) {
        Culture[] v = values();
        return id >= 0 && id < v.length ? v[id] : PLAINS;
    }
}

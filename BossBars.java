package com.agustin.bloodmoon.entity;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Barra de vida estilo jefe visible para los jugadores dentro de un radio. */
public final class BossBars {
    private BossBars() {}

    public static void update(Mob mob, ServerBossEvent bar, double range) {
        bar.setProgress(Math.max(0F, mob.getHealth() / mob.getMaxHealth()));
        if (mob.tickCount % 10 != 0 || !(mob.level() instanceof ServerLevel level)) return;

        double r2 = range * range;
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
            if (p.isRemoved() || p.level() != level || p.distanceToSqr(mob) > r2) bar.removePlayer(p);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) <= r2 && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }
}

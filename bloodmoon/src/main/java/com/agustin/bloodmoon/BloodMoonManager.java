package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.BloodMoonPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lógica del ciclo: decide cuándo empieza/termina la Luna de Sangre y sincroniza a los clientes.
 * Solo afecta al Overworld.
 */
public final class BloodMoonManager {
    /** Una Luna de Sangre cada N días (la noche del día 2, 5, 8, ...). */
    public static final int CYCLE_DAYS = 3;
    /** Ventana de "noche" en ticks del día (vanilla: atardecer ~12000, amanecer ~23000). */
    public static final long NIGHT_START = 13000L;
    public static final long NIGHT_END = 23000L;

    /** Cache del estado en el hilo del servidor. */
    private static volatile boolean active = false;
    /** true solo mientras se ejecuta el tick del Overworld durante una Luna de Sangre (lo lee el mixin del mob cap). */
    private static volatile boolean spawnBoost = false;

    private BloodMoonManager() {}

    public static boolean isActive() {
        return active;
    }

    public static boolean isSpawnBoostActive() {
        return spawnBoost;
    }

    public static boolean isScheduledDay(long day) {
        return day % CYCLE_DAYS == CYCLE_DAYS - 1;
    }

    public static boolean isNight(long dayTime) {
        long t = dayTime % 24000L;
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** Fuerza la Luna de Sangre en la próxima noche que empiece. Devuelve el índice de día de esa noche. */
    public static long forceNextNight(ServerLevel overworld) {
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long target = (dayTime % 24000L) < NIGHT_START ? day : day + 1;
        BloodMoonData.get(overworld).setForcedNightDay(target);
        return target;
    }

    public static boolean cancelForce(ServerLevel overworld) {
        BloodMoonData data = BloodMoonData.get(overworld);
        boolean had = data.getForcedNightDay() >= 0;
        data.setForcedNightDay(-1L);
        return had;
    }

    /** Días hasta la próxima Luna de Sangre programada (0 = esta noche). */
    public static long daysUntilScheduled(ServerLevel overworld) {
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        boolean nightPassed = (dayTime % 24000L) >= NIGHT_END;
        for (long d = 0; d <= CYCLE_DAYS; d++) {
            long candidate = day + d;
            if (d == 0 && nightPassed) continue;
            if (isScheduledDay(candidate)) return d;
        }
        return CYCLE_DAYS;
    }

    // ---------------------------------------------------------------- eventos

    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            spawnBoost = active && level.dimension() == Level.OVERWORLD;
        }
    }

    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        spawnBoost = false;
        if (level.dimension() != Level.OVERWORLD) return;

        BloodMoonData data = BloodMoonData.get(level);
        active = data.isActive();

        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        boolean night = isNight(dayTime);

        // Un forzado cuya noche ya pasó (p. ej. /time set) se descarta.
        if (data.getForcedNightDay() >= 0 && day > data.getForcedNightDay()) {
            data.setForcedNightDay(-1L);
        }

        if (!data.isActive()) {
            boolean forced = data.getForcedNightDay() == day;
            if (night && (isScheduledDay(day) || forced)) {
                if (forced) data.setForcedNightDay(-1L);
                setActive(level, data, true);
            }
        } else if (!night) {
            setActive(level, data, false);
        }
    }

    private static void setActive(ServerLevel overworld, BloodMoonData data, boolean value) {
        data.setActive(value);
        active = value;
        BloodMoonMod.LOGGER.info("Blood Moon {}", value ? "started" : "ended");

        Component msg = Component.translatable(value ? "bloodmoon.message.start" : "bloodmoon.message.end")
                .withStyle(value ? ChatFormatting.DARK_RED : ChatFormatting.GRAY);
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(msg);
        }
        PacketDistributor.sendToPlayersInDimension(overworld, new BloodMoonPayload(value));
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, player.level().dimension() == Level.OVERWORLD);
        }
    }

    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, event.getTo() == Level.OVERWORLD);
        }
    }

    private static void sync(ServerPlayer player, boolean inOverworld) {
        boolean value = inOverworld && BloodMoonData.get(player.server.overworld()).isActive();
        PacketDistributor.sendToPlayer(player, new BloodMoonPayload(value));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        active = false;
        spawnBoost = false;
    }
}

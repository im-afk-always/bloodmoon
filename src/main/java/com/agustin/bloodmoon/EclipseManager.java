package com.agustin.bloodmoon;

import com.agustin.bloodmoon.network.EclipsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Eclipse Solar (Overworld, de día). Se programa por comando o cada N días (config). El servidor solo decide el día y
 * avisa a los clientes; la secuencia visual la calcula cada cliente con la hora del mundo (Eclipse.at).
 * En la totalidad despiertan criaturas de la noche y, mientras dura la oscuridad, los no-muertos no se queman.
 */
public final class EclipseManager {
    private static final long MORNING = 400L;
    private static long announcedStart = -1, announcedTotal = -1, announcedEnd = -1;
    /** true mientras el sol está casi tapado (lo lee el mixin de quemadura solar). */
    private static volatile boolean dark;

    private EclipseManager() {}

    public static boolean suppressSunBurn(Level level) {
        return dark && !level.isClientSide && level.dimension() == Level.OVERWORLD && BloodMoonConfig.ECLIPSE_MOBS.get();
    }

    /**
     * Empieza un eclipse ya: lleva la hora a la mañana temprano (el sol recién salido). Unos 40 s después la luna asoma
     * por el horizonte, lo persigue más rápido y lo alcanza a ~61° de altura.
     */
    public static void startNow(ServerLevel overworld) {
        long day = overworld.getDayTime() / 24000L;
        if (overworld.getDayTime() % 24000L > MORNING) day++;   // ya pasó la mañana: el de mañana
        BloodMoonData.get(overworld).setEclipseDay(day);
        for (ServerLevel l : overworld.getServer().getAllLevels()) l.setDayTime(day * 24000L + MORNING);
        resetAnnouncements();
        broadcast(overworld);
    }

    public static boolean cancel(ServerLevel overworld) {
        BloodMoonData data = BloodMoonData.get(overworld);
        boolean had = data.getEclipseDay() >= 0;
        data.setEclipseDay(-1);
        dark = false;
        broadcast(overworld);
        return had;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BloodMoonData data = BloodMoonData.get(level);
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long tod = dayTime % 24000L;

        // programación automática
        int every = BloodMoonConfig.ECLIPSE_INTERVAL.get();
        if (every > 0 && day > 0 && day % every == 0 && data.getEclipseDay() != day && tod < Eclipse.START - 100) {
            data.setEclipseDay(day);
            resetAnnouncements();
            broadcast(level);
        }

        long eday = data.getEclipseDay();
        if (eday < 0) {
            dark = false;
            return;
        }
        if (day > eday || (day == eday && tod > Eclipse.END + 400)) {     // terminó
            data.setEclipseDay(-1);
            dark = false;
            broadcast(level);
            return;
        }
        if (day != eday) return;
        Eclipse.State st = Eclipse.at(tod);
        dark = st.coverage() > 0.9F;

        if (tod >= Eclipse.START && announcedStart != eday) {
            announcedStart = eday;
            message(level, "bloodmoon.eclipse.start", ChatFormatting.GOLD);
            sound(level, SoundEvents.BELL_RESONATE, 0.6F);
        }
        if (st.totality() && announcedTotal != eday) {
            announcedTotal = eday;
            message(level, "bloodmoon.eclipse.totality", ChatFormatting.GRAY);
            if (BloodMoonConfig.ECLIPSE_MOBS.get()) wakeCreatures(level);
        }
        if (tod >= Eclipse.END && announcedEnd != eday) {
            announcedEnd = eday;
            message(level, "bloodmoon.eclipse.end", ChatFormatting.YELLOW);
        }
    }

    /** En la totalidad, la noche despierta un rato: unas criaturas alrededor de cada jugador a cielo abierto. */
    private static void wakeCreatures(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        RandomSource r = level.random;
        EntityType<?>[] kinds = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ZOMBIE};
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !level.canSeeSky(p.blockPosition())) continue;
            int n = 2 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, dist = 18 + r.nextDouble() * 14;
                int x = (int) Math.floor(p.getX() + Math.cos(a) * dist), z = (int) Math.floor(p.getZ() + Math.sin(a) * dist);
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!level.getBlockState(top.below()).isSolid()) continue;
                var e = kinds[r.nextInt(kinds.length)].create(level);
                if (!(e instanceof Mob mob)) continue;
                mob.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, r.nextFloat() * 360F, 0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.EVENT, null);
                level.addFreshEntityWithPassengers(mob);
            }
        }
    }

    private static void message(ServerLevel level, String key, ChatFormatting color) {
        Component msg = Component.translatable(key).withStyle(color, ChatFormatting.ITALIC);
        for (ServerPlayer p : level.players()) p.sendSystemMessage(msg);
    }

    private static void sound(ServerLevel level, net.minecraft.sounds.SoundEvent s, float pitch) {
        for (ServerPlayer p : level.players()) p.playNotifySound(s, SoundSource.AMBIENT, 1.2F, pitch);
    }

    private static void resetAnnouncements() {
        announcedStart = announcedTotal = announcedEnd = -1;
    }

    // ---------------------------------------------------------------- sincronización

    public static void broadcast(ServerLevel overworld) {
        PacketDistributor.sendToPlayersInDimension(overworld, new EclipsePayload(BloodMoonData.get(overworld).getEclipseDay()));
    }

    private static void sync(ServerPlayer player, boolean inOverworld) {
        long day = inOverworld ? BloodMoonData.get(player.server.overworld()).getEclipseDay() : -1L;
        PacketDistributor.sendToPlayer(player, new EclipsePayload(day));
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, player.level().dimension() == Level.OVERWORLD);
    }

    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, event.getTo() == Level.OVERWORLD);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        dark = false;
        resetAnnouncements();
    }
}

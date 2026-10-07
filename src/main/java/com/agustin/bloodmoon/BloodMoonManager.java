package com.agustin.bloodmoon;

import com.agustin.bloodmoon.entity.ApocalypseRider;
import com.agustin.bloodmoon.entity.CursedCreeper;
import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.network.BloodMoonPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ciclo de lunas (solo Overworld): decide cuándo empieza/termina cada una, sincroniza clientes
 * y maneja los spawns propios de la Súper Luna de Sangre.
 */
public final class BloodMoonManager {
    public static final long NIGHT_START = 13000L;
    public static final long NIGHT_END = 23000L;
    private static final long MIDNIGHT = 18000L;

    private static volatile MoonType current = MoonType.NONE;
    /** true solo mientras se tickea el Overworld en Luna de Sangre (lo lee MobCategoryMixin). */
    private static volatile boolean spawnBoost = false;

    // Estado por noche de Súper Luna (en memoria; se reinicia al empezar cada una)
    private static final List<BlockPos> pendingCursed = new ArrayList<>();
    private static final Map<UUID, Integer> phantomCooldown = new HashMap<>();
    private static final Set<UUID> ridersSpawned = new HashSet<>();

    private BloodMoonManager() {}

    public static MoonType current() {
        return current;
    }

    public static boolean isSpawnBoostActive() {
        return spawnBoost;
    }

    // ---------------------------------------------------------------- calendario

    /** Luna programada para la noche del día indicado (noche n = día + 1). */
    public static MoonType scheduledFor(long day) {
        long night = day + 1;
        if (every(night, BloodMoonConfig.MOONLESS_INTERVAL.get())) return MoonType.MOONLESS;
        if (every(night, BloodMoonConfig.SUPER_INTERVAL.get())) return MoonType.SUPER;
        if (every(night, BloodMoonConfig.BLOOD_INTERVAL.get())) return MoonType.BLOOD;
        if (every(night, BloodMoonConfig.GOLDEN_INTERVAL.get())) return MoonType.GOLDEN;
        return MoonType.NONE;
    }

    private static boolean every(long night, int interval) {
        return interval > 0 && night % interval == 0;
    }

    public static boolean isNight(long dayTime) {
        long t = dayTime % 24000L;
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** Días hasta la próxima noche de ese tipo (0 = esta noche). -1 si está desactivada. */
    public static long daysUntil(ServerLevel overworld, MoonType type) {
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        boolean nightPassed = (dayTime % 24000L) >= NIGHT_END;
        for (long d = 0; d <= 2000; d++) {
            if (d == 0 && nightPassed) continue;
            if (scheduledFor(day + d) == type) return d;
        }
        return -1;
    }

    /** Fuerza una luna en la próxima noche que empiece. Devuelve true si es esta noche. */
    public static boolean forceNextNight(ServerLevel overworld, MoonType type) {
        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        boolean tonight = (dayTime % 24000L) < NIGHT_START;
        BloodMoonData.get(overworld).setForced(type, tonight ? day : day + 1);
        return tonight;
    }

    public static boolean cancelForce(ServerLevel overworld) {
        BloodMoonData data = BloodMoonData.get(overworld);
        boolean had = data.getForcedNightDay() >= 0;
        data.clearForced();
        return had;
    }

    // ---------------------------------------------------------------- eventos

    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            spawnBoost = current.isBlood() && level.dimension() == Level.OVERWORLD;
        }
    }

    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        spawnBoost = false;
        if (level.dimension() != Level.OVERWORLD) return;

        BloodMoonData data = BloodMoonData.get(level);
        current = data.getActive();

        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        boolean night = isNight(dayTime);

        if (data.getForcedNightDay() >= 0 && day > data.getForcedNightDay()) {
            data.clearForced();
        }

        if (current == MoonType.NONE) {
            if (night) {
                MoonType next = data.getForcedNightDay() == day ? data.getForcedType() : scheduledFor(day);
                if (next != MoonType.NONE) {
                    if (data.getForcedNightDay() == day) data.clearForced();
                    setActive(level, data, next);
                }
            }
        } else if (!night) {
            setActive(level, data, MoonType.NONE);
        }

        if (current == MoonType.SUPER) {
            tickSuper(level, dayTime % 24000L);
        }
    }

    private static void setActive(ServerLevel overworld, BloodMoonData data, MoonType type) {
        MoonType previous = current;
        data.setActive(type);
        current = type;
        pendingCursed.clear();
        phantomCooldown.clear();
        ridersSpawned.clear();
        BloodMoonMod.LOGGER.info("Moon changed: {} -> {}", previous, type);

        String key = type == MoonType.NONE
                ? "bloodmoon.message." + previous.key() + ".end"
                : "bloodmoon.message." + type.key() + ".start";
        ChatFormatting color = switch (type == MoonType.NONE ? previous : type) {
            case GOLDEN -> ChatFormatting.GOLD;
            case MOONLESS -> ChatFormatting.DARK_PURPLE;
            case SUPER -> ChatFormatting.RED;
            default -> ChatFormatting.DARK_RED;
        };
        Component msg = Component.translatable(key).withStyle(type == MoonType.NONE ? ChatFormatting.GRAY : color);
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(msg);
        }
        PacketDistributor.sendToPlayersInDimension(overworld, new BloodMoonPayload(type.ordinal()));
    }

    // ---------------------------------------------------------------- Súper Luna de Sangre

    /** Lo llama MobBuffs cuando cancela un creeper natural para reemplazarlo por un Cursed Creeper. */
    public static void queueCursedCreeper(BlockPos pos) {
        pendingCursed.add(pos.immutable());
    }

    private static void tickSuper(ServerLevel level, long timeOfDay) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            pendingCursed.clear();
            return;
        }

        // 1) Cursed Creepers que reemplazan a creepers naturales
        for (BlockPos pos : pendingCursed) {
            CursedCreeper creeper = ModEntities.CURSED_CREEPER.get().create(level);
            if (creeper == null) continue;
            creeper.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
            creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            level.addFreshEntity(creeper);
        }
        pendingCursed.clear();

        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            // 2) Phantoms gigantes (sin necesidad de insomnio)
            int cooldown = phantomCooldown.getOrDefault(player.getUUID(), randomPhantomDelay(random)) - 1;
            if (cooldown <= 0) {
                spawnPhantoms(level, player, random);
                cooldown = randomPhantomDelay(random);
            }
            phantomCooldown.put(player.getUUID(), cooldown);

            // 3) Jinete del Apocalipsis: uno por jugador a partir de la medianoche
            if (BloodMoonConfig.RIDER_ENABLED.get() && timeOfDay >= MIDNIGHT
                    && !ridersSpawned.contains(player.getUUID())) {
                ridersSpawned.add(player.getUUID());
                BlockPos pos = findRiderSpot(level, player, random);
                if (pos != null) ApocalypseRider.spawnWithMount(level, pos);
            }
        }
    }

    private static int randomPhantomDelay(RandomSource random) {
        int min = BloodMoonConfig.PHANTOM_MIN_SECONDS.get();
        int max = Math.max(min, BloodMoonConfig.PHANTOM_MAX_SECONDS.get());
        return (min + random.nextInt(max - min + 1)) * 20;
    }

    private static void spawnPhantoms(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos playerPos = player.blockPosition();
        if (!level.canSeeSky(playerPos)) return;

        int count = 1 + random.nextInt(level.getDifficulty().getId() + 1);
        for (int i = 0; i < count; i++) {
            BlockPos pos = playerPos.above(20 + random.nextInt(15)).east(-10 + random.nextInt(21)).south(-10 + random.nextInt(21));
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            Phantom phantom = EntityType.PHANTOM.create(level);
            if (phantom == null) continue;
            phantom.moveTo(pos, 0F, 0F);
            phantom.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
            level.addFreshEntity(phantom); // MobBuffs lo agranda y le duplica el daño
        }
    }

    /** Superficie a 24-40 bloques del jugador con espacio libre para el jinete gigante. */
    private static BlockPos findRiderSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 24 + random.nextInt(17);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos column = new BlockPos(x, 0, z);
            if (!level.isLoaded(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos.below()).isEmpty()) continue;
            boolean clear = true;
            for (int dy = 0; dy < 9 && clear; dy++) {
                clear = level.getBlockState(pos.above(dy)).isAir();
            }
            if (clear) return pos;
        }
        return null;
    }

    // ---------------------------------------------------------------- sincronización

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
        MoonType type = inOverworld ? BloodMoonData.get(player.server.overworld()).getActive() : MoonType.NONE;
        PacketDistributor.sendToPlayer(player, new BloodMoonPayload(type.ordinal()));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        current = MoonType.NONE;
        spawnBoost = false;
        pendingCursed.clear();
        phantomCooldown.clear();
        ridersSpawned.clear();
    }
}

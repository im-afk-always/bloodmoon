package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.BloodMoonConfig;
import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.VoidEye;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.world.design.LabyrinthDesign;
import com.agustin.bloodmoon.world.design.SanctumDesign;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Despierta al Observador cuando un jugador pisa la plataforma de un Santuario del Ojo (Laberinto del Vacío).
 * Tras derrotarlo, ese Santuario duerme unos días (config "eyeRespawnDays").
 */
public final class EyeSanctums {
    private static final int TRIGGER_R = 38;

    private EyeSanctums() {}

    public static LabyrinthDesign design(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof LabyrinthChunkGenerator gen) {
            return gen.design(level.getChunkSource().randomState());
        }
        return null;
    }

    /** Centro del estrado (piso) del Santuario más cercano, o null fuera del Laberinto. */
    public static BlockPos nearest(ServerLevel level, BlockPos from) {
        LabyrinthDesign d = design(level);
        if (d == null) return null;
        int[] c = d.nearestSanctum(from.getX(), from.getZ());
        return c == null ? null : new BlockPos(c[0], LabyrinthDesign.FLOOR, c[1]);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ModDimensions.VOID_LABYRINTH) return;
        if (level.getGameTime() % 20 != 3) return;
        LabyrinthDesign d = design(level);
        if (d == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.onGround()) continue;
            int[] c = d.nearestSanctum(p.getBlockX(), p.getBlockZ());
            if (c == null) continue;
            double dx = p.getX() - (c[0] + 0.5), dz = p.getZ() - (c[1] + 0.5);
            if (dx * dx + dz * dz > TRIGGER_R * TRIGGER_R) continue;
            if (p.getY() < LabyrinthDesign.FLOOR - 1 || p.getY() > LabyrinthDesign.FLOOR + 16) continue;
            Vec3 home = new Vec3(c[0] + 0.5, LabyrinthDesign.FLOOR, c[1] + 0.5);
            if (!level.getEntitiesOfClass(VoidEye.class, new AABB(BlockPos.containing(home)).inflate(150)).isEmpty()) continue;
            Data data = Data.get(level);
            long until = data.sleepUntil(BlockPos.containing(home));
            if (level.getGameTime() < until) {
                if (level.getGameTime() % 200 == 3) {
                    long days = (until - level.getGameTime()) / 24000L + 1;
                    p.displayClientMessage(Component.translatable("bloodmoon.eye.sleeping", days).withStyle(ChatFormatting.DARK_PURPLE), true);
                }
                continue;
            }
            spawn(level, home);
        }
    }

    /** Invoca al Observador con su despertar; home = centro del estrado (a la altura del piso). */
    public static VoidEye spawn(ServerLevel level, Vec3 home) {
        VoidEye eye = ModEntities.VOID_EYE.get().create(level);
        if (eye == null) return null;
        eye.moveTo(home.x, home.y + SanctumDesign.EYE_HEIGHT - VoidEye.RADIUS, home.z, 0F, 0F);
        eye.setHome(home);
        eye.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(home)), MobSpawnType.EVENT, null);
        level.addFreshEntity(eye);
        return eye;
    }

    public static void markDefeated(ServerLevel level, Vec3 home) {
        int days = BloodMoonConfig.EYE_RESPAWN_DAYS.get();
        Data.get(level).sleep(BlockPos.containing(home), level.getGameTime() + days * 24000L);
    }

    public static final class Data extends SavedData {
        private static final String NAME = "bloodmoon_eye_sanctums";
        private final Map<Long, Long> sleeping = new HashMap<>();

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("sleeping", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                d.sleeping.put(e.getLong("pos"), e.getLong("until"));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            sleeping.forEach((pos, until) -> {
                CompoundTag e = new CompoundTag();
                e.putLong("pos", pos);
                e.putLong("until", until);
                list.add(e);
            });
            tag.put("sleeping", list);
            return tag;
        }

        long sleepUntil(BlockPos pos) {
            return sleeping.getOrDefault(pos.asLong(), 0L);
        }

        void sleep(BlockPos pos, long until) {
            sleeping.put(pos.asLong(), until);
            setDirty();
        }
    }
}

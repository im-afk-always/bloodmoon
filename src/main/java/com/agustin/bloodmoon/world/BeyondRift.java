package com.agustin.bloodmoon.world;

import com.agustin.bloodmoon.entity.ModEntities;
import com.agustin.bloodmoon.entity.UnboundObserver;
import com.agustin.bloodmoon.entity.VoidEye;
import com.agustin.bloodmoon.registry.ModDimensions;
import com.agustin.bloodmoon.world.design.BeyondDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El viaje al Más Allá de la Grieta y la vuelta.
 * - drag: el Observador vencido en su Santuario se lleva a todos los jugadores de la arena a un círculo nuevo
 *   del Más Allá y renace como el Observador Desatado.
 * - Cada jugador guarda a dónde volver; al ganar vuelven al Santuario. Si no queda jefe (perdieron, se
 *   desconectaron, etc.), a los 10 s los devuelve solos: del Más Allá no se sale caminando.
 * - Morir en el Más Allá no hace perder el inventario: se devuelve al reaparecer.
 */
public final class BeyondRift {
    public static final String RETURN_KEY = "bloodmoon_beyond_return";
    private static final Map<UUID, Integer> STRANDED = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> STASH = new HashMap<>();

    private BeyondRift() {}

    /** @return false si el Más Allá no existe (el Observador muere en su Santuario). */
    public static boolean drag(ServerLevel from, VoidEye eye, List<ServerPlayer> players) {
        MinecraftServer server = from.getServer();
        ServerLevel beyond = server.getLevel(ModDimensions.BEYOND);
        if (beyond == null) return false;
        int k = Data.get(beyond).nextFight();
        int[] c = BeyondDesign.fightCenter(k);
        Vec3 base = new Vec3(c[0] + 0.5, BeyondDesign.FLOOR, c[1] + 0.5);
        beyond.getChunk(c[0] >> 4, c[1] >> 4);
        Vec3 home = eye.getHome();
        Vec3 back = home.add(12, 0.2, 0);

        int n = Math.max(1, players.size());
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", from.dimension().location().toString());
            tag.putDouble("x", back.x);
            tag.putDouble("y", back.y);
            tag.putDouble("z", back.z);
            p.getPersistentData().put(RETURN_KEY, tag);
            double a = Math.PI * 2 * i / n;
            double x = base.x + Math.cos(a) * 26, z = base.z + Math.sin(a) * 26;
            float yaw = (float) Math.toDegrees(Math.atan2(-(base.x - x), base.z - z));
            p.teleportTo(beyond, x, base.y + 0.1, z, yaw, 0F);
        }

        UnboundObserver boss = ModEntities.UNBOUND_OBSERVER.get().create(beyond);
        if (boss == null) return false;
        boss.moveTo(base.x, base.y + 150, base.z, 0F, 0F);   // desciende desde la Grieta
        boss.setHome(base);
        boss.setReturn(from.dimension(), home);
        boss.finalizeSpawn(beyond, beyond.getCurrentDifficultyAt(BlockPos.containing(base)), MobSpawnType.EVENT, null);
        beyond.addFreshEntity(boss);
        // el Santuario queda "ocupado" mientras dura la pelea
        EyeSanctums.Data.get(from).sleep(BlockPos.containing(home), from.getGameTime() + 6000);
        return true;
    }

    /** Devuelve al jugador a donde estaba antes del Más Allá (o al punto de aparición del mundo). */
    public static void sendBack(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CompoundTag tag = p.getPersistentData().getCompound(RETURN_KEY);
        ServerLevel level = null;
        Vec3 pos = null;
        if (tag.contains("dim")) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim")));
            level = server.getLevel(key);
            pos = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        }
        if (level == null) {
            level = server.overworld();
            BlockPos s = level.getSharedSpawnPos();
            pos = Vec3.atBottomCenterOf(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s));
        }
        p.getPersistentData().remove(RETURN_KEY);
        p.teleportTo(level, pos.x, pos.y, pos.z, p.getYRot(), 0F);
    }

    /** Jugadores varados en el Más Allá sin jefe: vuelven a los 10 s. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ModDimensions.BEYOND) return;
        if (level.getGameTime() % 20 != 9) return;
        for (ServerPlayer p : List.copyOf(level.players())) {
            boolean boss = !level.getEntitiesOfClass(UnboundObserver.class, p.getBoundingBox().inflate(300, 200, 300), e -> !e.isRemoved()).isEmpty();
            if (boss) {
                STRANDED.remove(p.getUUID());
                continue;
            }
            int t = STRANDED.merge(p.getUUID(), 1, Integer::sum);
            if (t >= 10) {
                STRANDED.remove(p.getUUID());
                sendBack(p);
            }
        }
    }

    /** Morir en el Más Allá: el inventario se guarda y se devuelve al reaparecer. */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != ModDimensions.BEYOND) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity e : event.getDrops()) items.add(e.getItem().copy());
        STASH.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(items);
        event.setCanceled(true);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        p.getPersistentData().remove(RETURN_KEY);
        List<ItemStack> items = STASH.remove(p.getUUID());
        if (items == null) return;
        for (ItemStack s : items) {
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
    }

    /** Recoge el botín y la experiencia que soltó el jefe en el Más Allá y los recrea en el Santuario. */
    public static void moveLoot(ServerLevel from, Vec3 at, ServerLevel to, Vec3 dest) {
        AABB box = new AABB(at, at).inflate(6);
        for (ItemEntity e : from.getEntitiesOfClass(ItemEntity.class, box)) {
            ItemEntity copy = new ItemEntity(to, dest.x, dest.y + 0.5, dest.z, e.getItem().copy());
            copy.setDefaultPickUpDelay();
            to.addFreshEntity(copy);
            e.discard();
        }
        int xp = 0;
        for (net.minecraft.world.entity.ExperienceOrb o : from.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box)) {
            xp += o.getValue();
            o.discard();
        }
        if (xp > 0) net.minecraft.world.entity.ExperienceOrb.award(to, dest, xp);
    }

    public static final class Data extends SavedData {
        private static final String NAME = "bloodmoon_beyond";
        private int fights;

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.fights = tag.getInt("fights");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("fights", fights);
            return tag;
        }

        int nextFight() {
            setDirty();
            return fights++;
        }
    }
}

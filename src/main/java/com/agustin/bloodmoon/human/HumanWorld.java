package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * La Humanidad reemplaza a los aldeanos: todo aldeano que entra al mundo se vuelve humano (con su oficio y su
 * nivel), los saqueadores y vindicadores se vuelven bandidos, y el vendedor ambulante deja de existir.
 * El reemplazo se hace en el tick siguiente (no se pueden agregar entidades mientras se carga un chunk).
 */
public final class HumanWorld {
    private HumanWorld() {}

    private static final Map<ServerLevel, List<Entity>> PENDING = new WeakHashMap<>();

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity e = event.getEntity();
        if (e instanceof WanderingTrader || e instanceof TraderLlama) {
            if (event.loadedFromDisk()) queue(level, e);
            else event.setCanceled(true);
            return;
        }
        if (e.getClass() == Villager.class || e instanceof Pillager || e instanceof Vindicator) queue(level, e);
    }

    private static void queue(ServerLevel level, Entity e) {
        PENDING.computeIfAbsent(level, k -> new ArrayList<>()).add(e);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<Entity> list = PENDING.get(level);
        if (list == null || list.isEmpty()) return;
        List<Entity> batch = new ArrayList<>(list);
        list.clear();
        for (Entity e : batch) {
            if (!e.isAlive() || e.level() != level) continue;
            if (e instanceof WanderingTrader || e instanceof TraderLlama) {
                e.discard();
                continue;
            }
            Human h = convert(level, e);
            if (h != null) e.discard();
        }
    }

    public static Culture cultureAt(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.HAS_VILLAGE_DESERT) || level.getBiome(pos).is(BiomeTags.HAS_DESERT_PYRAMID)
                ? Culture.DESERT : Culture.PLAINS;
    }

    /** Crea el humano equivalente en el lugar de {@code e} (no lo descarta). */
    public static Human convert(ServerLevel level, Entity e) {
        Human h = ModEntities.HUMAN.get().create(level);
        if (h == null) return null;
        h.moveTo(e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot());
        Culture culture = cultureAt(level, e.blockPosition());
        int seed = level.random.nextInt();
        if (e instanceof Villager v) {
            HumanJob job = jobOf(v.getVillagerData().getProfession());
            if (v.getVillagerData().getType() == net.minecraft.world.entity.npc.VillagerType.DESERT) culture = Culture.DESERT;
            h.setup(seed, job, culture);
            h.inheritTradeLevel(v.getVillagerData().getLevel(), v.getVillagerXp());
            if (v.isBaby()) h.setAge(v.getAge());
            if (v.hasCustomName()) h.setCustomName(v.getCustomName());
            if (v.isPersistenceRequired()) h.setPersistenceRequired();
        } else {
            h.setup(seed, HumanJob.BANDIT, culture);
            if (e instanceof Mob m && m.isPersistenceRequired()) h.setPersistenceRequired();
        }
        level.addFreshEntity(h);
        return h;
    }

    public static HumanJob jobOf(VillagerProfession p) {
        String id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).getPath();
        for (HumanJob j : HumanJob.values()) if (id.equals(j.vanilla)) return j;
        return HumanJob.NONE;
    }

    public static void clear() {
        PENDING.clear();
    }
}

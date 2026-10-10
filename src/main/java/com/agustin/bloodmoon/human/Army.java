package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Ejército de un asentamiento. Como datos: cuántos soldados quiere según su nivel (aldea 3, pueblo 6, ciudad 12,
 * capital 20, +4 con muralla), los recluta y les paga sueldo del tesoro, y mejora su equipo por etapas (cuero y piedra →
 * cuero completo con hierro → cota de malla → hierro completo y encantado), gastando armas y armaduras del mercado.
 * Con un jugador cerca mantiene a los soldados en pie: repone a los muertos, los equipa según la etapa y les da un
 * puesto (torres, portones, plaza) o una ronda (calles o adarve de la muralla).
 */
public final class Army {
    /** Soldados buscados por nivel (aldea, pueblo, ciudad, capital). */
    static final int[] TARGET = {3, 6, 12, 20};
    /** Costo de pasar a cada etapa de equipo. */
    static final int[] TIER_COST = {0, 600, 1500, 4000};
    /** Soldados en pie a la vez por asentamiento (el resto queda como datos). */
    public static final int LIVE_CAP = 16;
    /** Rondas: puesto fijo, calles, muralla. */
    public static final int POST = 0, STREETS = 1, WALL = 2;

    private Army() {}

    public static int target(Settlement s) {
        int t = TARGET[Math.max(0, Math.min(3, s.level))] + (s.wall.isEmpty() ? 0 : 4);
        return Math.min(t, Math.max(2, s.pop / 6));
    }

    static int recruitCost(Settlement s) {
        return 30 + 20 * s.armyTier;
    }

    static double wagePerDay(Settlement s) {
        return 3 + 2 * s.armyTier;
    }

    /** Un ciclo de la simulación: reclutar, licenciar, sueldos y mejoras de equipo. */
    static void cycle(Settlement s) {
        Market.ensure(s);
        int want = target(s);
        double spent = 0;
        int cost = recruitCost(s);
        if (s.soldiers < want && s.treasury >= cost * 2 && s.pop > s.soldiers + 8) {
            s.soldiers++;
            s.treasury -= cost;
            spent += cost;
        } else if (s.soldiers > want + 2) {
            s.soldiers--;   // licenciado: vuelve a ser civil
        }
        double wages = s.soldiers * wagePerDay(s) / HumanityManager.CYCLES_PER_DAY;
        if (s.treasury >= wages) {
            s.treasury -= wages;
            spent += wages;
        } else if (s.soldiers > 0 && RandomSource.create().nextInt(HumanityManager.CYCLES_PER_DAY) == 0) {
            s.soldiers--;   // sin paga, alguno deserta
        }
        // mejora de equipo: hace falta el dinero y que el mercado tenga armas y armaduras para todos
        int tierWant = Math.min(3, s.level);
        if (s.armyTier < tierWant) {
            int next = s.armyTier + 1;
            double w = s.stock[Good.WEAPONS.ordinal()], a = s.stock[Good.ARMOR.ordinal()];
            if (s.treasury >= TIER_COST[next] * 1.5 && w >= s.soldiers * 0.5 && a >= s.soldiers * 0.5) {
                s.treasury -= TIER_COST[next];
                s.stock[Good.WEAPONS.ordinal()] -= s.soldiers * 0.5;
                s.stock[Good.ARMOR.ordinal()] -= s.soldiers * 0.5;
                s.armyTier = next;
                spent += TIER_COST[next];
            }
        }
        s.militarySpend = s.militarySpend * (1 - 1.0 / HumanityManager.CYCLES_PER_DAY) + spent;
    }

    /**
     * Con un jugador cerca: cuenta los soldados en pie, repone los que faltan (hasta {@link #LIVE_CAP}) en las puertas de
     * torres, ayuntamiento y castillo o en la plaza, y actualiza el equipo de los que quedaron atrás de la etapa.
     */
    static void maintain(ServerLevel level, Settlement s, List<VillageLayout.Building> bs) {
        int reach = HumanityManager.influence(level, s) + 48;
        Player near = level.getNearestPlayer(s.x, s.y, s.z, reach + 64, false);
        if (near == null || !level.hasChunk(s.x >> 4, s.z >> 4)) return;
        AABB box = new AABB(s.x - reach, s.y - 48, s.z - reach, s.x + reach, s.y + 64, s.z + reach);
        List<Human> live = level.getEntitiesOfClass(Human.class, box, h -> h.isGuard() && h.settlement() == s.key);
        int[] roles = new int[3];
        for (Human h : live) {
            if (h.guardTier() < s.armyTier) h.equipGuard(s.armyTier);
            roles[h.guardRole()]++;
        }
        int want = Math.min(s.soldiers, LIVE_CAP);
        if (live.size() >= want) return;
        List<BlockPos> posts = new ArrayList<>();
        for (VillageLayout.Building b : bs) {
            if (b.job() == HumanJob.GUARD && level.hasChunk(b.coreX() >> 4, b.coreZ() >> 4)) posts.add(new BlockPos(b.coreX(), b.floorY() + 1, b.coreZ()));
        }
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        if (posts.isEmpty()) posts.add(new BlockPos(s.x + 3, lay.plaza().y0(), s.z + 3));
        boolean walls = false;
        for (boolean d : s.wallDone) walls |= d;
        RandomSource r = level.random;
        // rondas: un tercio en la muralla (si hay), la mitad del resto por las calles, los demás de guardia en su puesto
        int wallWant = walls ? want / 3 : 0, streetWant = (want - wallWant + 1) / 2;
        for (int k = 0; k < 3 && live.size() + k < want; k++) {
            int role = roles[WALL] < wallWant ? WALL : roles[STREETS] < streetWant ? STREETS : POST;
            BlockPos at = posts.get(r.nextInt(posts.size()));
            Human h = ModEntities.HUMAN.get().create(level);
            if (h == null) return;
            h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360F, 0F);
            h.setup(r.nextInt(), HumanJob.GUARD, s.culture);
            h.setSettlement(s.key);
            h.setHome(at);
            h.setGuardRole(role);
            h.equipGuard(s.armyTier);
            h.setPersistenceRequired();
            level.addFreshEntity(h);
            roles[role]++;
        }
    }

    /** Próximo punto de la ronda: un lugar de una calle pavimentada, o el adarve un poco más allá. Null si no hay. */
    public static BlockPos patrolPoint(ServerLevel level, Settlement s, int role, BlockPos from, int dir, RandomSource r) {
        if (role == WALL && !s.wall.isEmpty()) {
            // el tramo levantado más cercano y, desde ahí, dos o tres tramos en el sentido de la ronda
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < s.wall.size() && i < s.wallDone.length; i++) {
                if (!s.wallDone[i]) continue;
                VillageLayout.Road w = s.wall.get(i);
                double d = w.dist(from.getX(), from.getZ());
                if (d < bd) { bd = d; best = i; }
            }
            if (best >= 0) {
                int ring = best / HumanityManager.WALL_RAYS, n = HumanityManager.WALL_RAYS;
                for (int step = 2; step >= 1; step--) {
                    int k = ring * n + Math.floorMod(best % n + dir * step, n);
                    if (k >= s.wall.size() || k >= s.wallDone.length || !s.wallDone[k]) continue;
                    VillageLayout.Road w = s.wall.get(k);
                    if (w.y0() == Integer.MIN_VALUE) break;
                    int y = (int) Math.ceil(w.y0() / 2.0) + VillageBuilder.WALL_H + 1;
                    return new BlockPos((int) Math.round(w.x0()), y, (int) Math.round(w.z0()));
                }
            }
        }
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        HumanityManager.Net nn = HumanityManager.net(s, lay);
        List<Integer> paved = new ArrayList<>();
        for (int i = 0; i < nn.size() && i < s.paved.length; i++) if (s.paved[i]) paved.add(i);
        if (paved.isEmpty()) return null;
        // preferir tramos cercanos: la ronda recorre el barrio, no cruza la ciudad de punta a punta
        int pick = -1;
        double bd = Double.MAX_VALUE;
        for (int tries = 0; tries < 8; tries++) {
            int i = paved.get(r.nextInt(paved.size()));
            VillageLayout.Road w = nn.roads().get(i);
            double d = w.dist(from.getX(), from.getZ()) + r.nextDouble() * 40;
            if (d < bd && d > 4) { bd = d; pick = i; }
        }
        if (pick < 0) return null;
        VillageLayout.Road w = nn.roads().get(pick);
        double t = r.nextDouble();
        double x = w.x0() + (w.x1() - w.x0()) * t, z = w.z0() + (w.z1() - w.z0()) * t;
        int y = w.y(x, z);
        if (y == Integer.MIN_VALUE) y = VillageBuilder.ground(level, (int) Math.round(x), (int) Math.round(z), false) + 1;
        return new BlockPos((int) Math.round(x), y, (int) Math.round(z));
    }
}

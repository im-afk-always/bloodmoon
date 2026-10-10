package com.agustin.bloodmoon.human;

import com.agustin.bloodmoon.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Puerto pesquero: si hay un cuerpo de agua cerca (mar, río o lago), el asentamiento levanta muelles de madera sobre
 * pilotes que entran al agua, una casilla de pescadores y caminos hasta las calles. Crece con el asentamiento (una aldea
 * tiene un muelle; un pueblo, dos con espigones y botes; una ciudad, tres y un faro) mientras el agua lo permita: la
 * cantidad de muelles depende del tamaño del cuerpo de agua y su largo, de cuánto mar hay por delante (nunca cierra un
 * río). Cada muelle trae dos pescadores que pescan desde la punta y suman comida al pueblo.
 */
public final class Port {
    public static final int CELL = 4, MAX_PIERS = 5;
    private static final int[] OFFSETS = {0, 9, -9, 18, -18};

    private Port() {}

    /** Busca agua: la celda mojada más cercana a la plaza (a 16-140 bloques) y el tamaño de su cuerpo de agua. */
    static void plan(ServerLevel level, Settlement s) {
        s.portChecked = true;
        StreetPlanner.Terrain t = VillageLayout.terrain(level);
        // de la plaza hacia afuera: el primer cuerpo de agua que valga la pena (los charcos se saltan)
        Set<Long> visited = new HashSet<>();
        int bx = 0, bz = 0, size = 0;
        boolean found = false;
        for (int r = 4; r <= 35 && !found; r++) {
            for (int i = -r; i <= r && !found; i++) {
                for (int k = 0; k < 4 && !found; k++) {
                    int cx = k == 0 ? i : k == 1 ? i : k == 2 ? -r : r;
                    int cz = k == 0 ? -r : k == 1 ? r : i;
                    int x = s.x + cx * CELL, z = s.z + cz * CELL;
                    if (visited.contains(key(x, z)) || !t.wet(x, z)) continue;
                    int n = flood(t, x, z, visited);
                    if (n >= 10) {
                        found = true;
                        bx = x;
                        bz = z;
                        size = n;
                    }
                }
            }
        }
        if (!found) return;
        // orilla: caminando desde la plaza hacia el agua, el último punto seco
        double dx = bx - s.x, dz = bz - s.z, len = Math.hypot(dx, dz);
        int sx = s.x, sz = s.z, wx = bx, wz = bz;
        for (double d = 0; d <= len; d += 1) {
            int x = (int) Math.round(s.x + dx / len * d), z = (int) Math.round(s.z + dz / len * d);
            if (t.wet(x, z)) {
                wx = x;
                wz = z;
                break;
            }
            sx = x;
            sz = z;
        }
        int ax = wx - sx, az = wz - sz;
        if (Math.abs(ax) >= Math.abs(az)) {
            s.portDX = Integer.signum(ax == 0 ? (int) Math.signum(dx) : ax);
            s.portDZ = 0;
        } else {
            s.portDX = 0;
            s.portDZ = Integer.signum(az);
        }
        if (s.portDX == 0 && s.portDZ == 0) s.portDX = 1;
        s.portX = sx;
        s.portZ = sz;
        s.portY = t.height(wx, wz) - 1;
        s.portSize = size;
    }

    public static boolean has(Settlement s) {
        return s.portSize > 0;
    }

    public static int maxPiers(Settlement s) {
        return Math.max(1, Math.min(MAX_PIERS, s.portSize / 12));
    }

    /** Muelles que quiere tener según su nivel (aldea 1 … capital 4), limitados por el agua. */
    public static int wantPiers(Settlement s) {
        return Math.min(maxPiers(s), 1 + s.level);
    }

    /** Geometría de un muelle: {anclaX, anclaZ, largo, alturaCubierta} o null si ahí no hay agua útil. */
    static int[] pier(ServerLevel level, Settlement s, int i) {
        StreetPlanner.Terrain t = VillageLayout.terrain(level);
        int px = -s.portDZ, pz = s.portDX;
        int ox = s.portX + px * OFFSETS[i], oz = s.portZ + pz * OFFSETS[i];
        // buscar la entrada al agua sobre esta línea
        int ex = Integer.MIN_VALUE, ez = 0;
        int ax = ox - s.portDX * 8, az = oz - s.portDZ * 8;
        for (int d = -8; d <= 24; d++) {
            int x = ox + s.portDX * d, z = oz + s.portDZ * d;
            if (t.wet(x, z)) {
                ex = x;
                ez = z;
                break;
            }
            ax = x;
            az = z;
        }
        if (ex == Integer.MIN_VALUE) return null;
        int maxLen = 12 + 5 * s.level;
        int len = 0;
        for (int d = 1; d <= maxLen; d++) {
            int x = ax + s.portDX * d, z = az + s.portDZ * d;
            int fx = x + s.portDX * 6, fz = z + s.portDZ * 6;
            if (!t.wet(x, z) || !t.wet(fx, fz)) break;   // deja siempre agua libre adelante
            len = d;
        }
        if (len < 5) return null;
        return new int[]{ax, az, len, t.height(ex, ez)};
    }

    /** Coloca el siguiente muelle pendiente si sus chunks están cargados. */
    static void build(ServerLevel level, HumanityManager.Data data, Settlement s) {
        if (!has(s)) return;
        if (s.pierDone.length != MAX_PIERS) s.pierDone = java.util.Arrays.copyOf(s.pierDone, MAX_PIERS);
        for (int i = 0; i < Math.min(s.portPiers, MAX_PIERS); i++) {
            if (s.pierDone[i]) continue;
            int[] g = pier(level, s, i);
            if (g == null) {
                s.pierDone[i] = true;   // no hay agua útil en esa línea: se salta
                data.setDirty();
                return;
            }
            int[] box = box(s, g);
            if (!HumanityManager.loadedBox(level, box)) return;
            buildPier(level, data, s, i, g);
            s.pierDone[i] = true;
            data.setDirty();
            return;
        }
        // faro de ciudad en la punta del primer muelle
        if (s.level >= Settlement.CITY && !s.portLighthouse && s.pierDone.length > 0 && s.pierDone[0] && s.portPiers > 0) {
            int[] g = pier(level, s, 0);
            if (g == null) {
                s.portLighthouse = true;
                return;
            }
            int[] box = box(s, g);
            if (!HumanityManager.loadedBox(level, box)) return;
            lighthouse(level, s, g);
            s.portLighthouse = true;
            data.setDirty();
        }
    }

    private static int[] box(Settlement s, int[] g) {
        int ex = g[0] + s.portDX * (g[2] + 3), ez = g[1] + s.portDZ * (g[2] + 3);
        return new int[]{Math.min(g[0], ex) - 8, Math.min(g[1], ez) - 8, Math.max(g[0], ex) + 8, Math.max(g[1], ez) + 8};
    }

    private static void buildPier(ServerLevel level, HumanityManager.Data data, Settlement s, int i, int[] g) {
        boolean desert = s.culture == Culture.DESERT;
        boolean stone = s.level >= Settlement.CITY && i == 0;
        BlockState deck = (stone ? Blocks.STONE_BRICKS : desert ? Blocks.ACACIA_PLANKS : Blocks.SPRUCE_PLANKS).defaultBlockState();
        BlockState post = (stone ? Blocks.STONE_BRICKS : desert ? Blocks.STRIPPED_ACACIA_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
        BlockState fence = (desert ? Blocks.ACACIA_FENCE : Blocks.SPRUCE_FENCE).defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int dx = s.portDX, dz = s.portDZ, px = -dz, pz = dx;
        int y = g[3];   // primer bloque libre sobre el agua: la cubierta queda a ras
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int len = g[2];
        for (int d = -2; d <= len; d++) {
            boolean end = d >= len - 2;
            int half = end ? 2 : 1;
            for (int w = -half; w <= half; w++) {
                int x = g[0] + dx * d + px * w, z = g[1] + dz * d + pz * w;
                p.set(x, y, z);
                BlockState cur = level.getBlockState(p);
                if (d < 0 && !cur.isAir() && cur.getFluidState().isEmpty() && !VillageBuilder.natural(cur)) continue;
                level.setBlock(p, deck, VillageBuilder.FLAGS);
                for (int k = 1; k <= 3; k++) {
                    BlockState st = level.getBlockState(p.set(x, y + k, z));
                    if (!st.isAir() && st.getFluidState().isEmpty() && VillageBuilder.natural(st)) level.setBlock(p, air, VillageBuilder.FLAGS);
                }
                // pilotes hasta el fondo
                boolean pile = (Math.abs(w) == half) && (d % 4 == 0 || d == len);
                if (pile && d >= 0) {
                    for (int yy = y - 1, n = 0; n < 24; yy--, n++) {
                        BlockState st = level.getBlockState(p.set(x, yy, z));
                        if (!st.isAir() && st.getFluidState().isEmpty()) break;
                        level.setBlock(p, post, VillageBuilder.FLAGS);
                    }
                    level.setBlock(p.set(x, y + 1, z), fence, VillageBuilder.FLAGS);
                    if (d % 8 == 0 || d == len) level.setBlock(p.set(x, y + 2, z), Blocks.LANTERN.defaultBlockState(), VillageBuilder.FLAGS);
                }
            }
        }
        // espigones con botes amarrados (desde pueblo)
        if (s.level >= Settlement.TOWN) {
            for (int d = 6; d < len - 3; d += 6) {
                int side = (d / 6) % 2 == 0 ? 1 : -1;
                for (int k = 2; k <= 5; k++) {
                    int x = g[0] + dx * d + px * side * k, z = g[1] + dz * d + pz * side * k;
                    p.set(x, y, z);
                    BlockState cur = level.getBlockState(p);
                    if (cur.getFluidState().isEmpty() && !cur.isAir()) break;
                    level.setBlock(p, deck, VillageBuilder.FLAGS);
                    if (k == 5) {
                        for (int yy = y - 1, n = 0; n < 24; yy--, n++) {
                            BlockState st = level.getBlockState(p.set(x, yy, z));
                            if (!st.isAir() && st.getFluidState().isEmpty()) break;
                            level.setBlock(p, post, VillageBuilder.FLAGS);
                        }
                        level.setBlock(p.set(x, y + 1, z), fence, VillageBuilder.FLAGS);
                        // bote al costado del espigón
                        int bxp = x + dx * 2, bzp = z + dz * 2;
                        if (level.getBlockState(new BlockPos(bxp, y - 1, bzp)).getFluidState().isEmpty()) continue;
                        Boat boat = net.minecraft.world.entity.EntityType.BOAT.create(level);
                        if (boat != null) {
                            boat.moveTo(bxp + 0.5, y - 0.5, bzp + 0.5, Direction.fromDelta(px * side, 0, pz * side).toYRot(), 0);
                            boat.setVariant(desert ? Boat.Type.ACACIA : Boat.Type.SPRUCE);
                            level.addFreshEntity(boat);
                        }
                    }
                }
            }
        }
        // primer muelle: casilla de pescadores en tierra y camino a las calles
        if (i == 0) {
            hut(level, s, g, desert);
            road(level, s, g, desert);
        }
        // pescadores en la punta
        int room = Math.max(0, HumanityManager.MATERIAL_CAP - s.materialized);
        for (int k = 0; k < Math.min(2, room); k++) {
            Human h = ModEntities.HUMAN.get().create(level);
            if (h == null) continue;
            int x = g[0] + dx * len + px * (k == 0 ? -1 : 1), z = g[1] + dz * len + pz * (k == 0 ? -1 : 1);
            h.moveTo(x + 0.5, y + 1, z + 0.5, Direction.fromDelta(dx, 0, dz).toYRot(), 0);
            h.setup(level.random.nextInt(), HumanJob.FISHERMAN, s.culture);
            h.setHome(new BlockPos(x, y + 1, z));
            h.setFisher(new BlockPos(x + dx * 5, y - 1, z + dz * 5));
            h.setSettlement(s.key);
            h.setPersistenceRequired();
            level.addFreshEntity(h);
            s.materialized++;
        }
    }

    private static void hut(ServerLevel level, Settlement s, int[] g, boolean desert) {
        String name = (desert ? "desert/" : "plains/") + "fishing_hut";
        Rotation rot = net.minecraft.world.level.block.Rotation.NONE;
        // la puerta mira hacia el muelle (+z de la plantilla = dirección al agua)
        rot = com.agustin.bloodmoon.invasion.DominionTemplates.facing(0, 0, s.portDX * 10, s.portDZ * 10);
        int px = -s.portDZ, pz = s.portDX;
        int cx = g[0] - s.portDX * 2 + px * 5, cz = g[1] - s.portDZ * 2 + pz * 5;
        int floorY = VillageBuilder.ground(level, cx, cz, false);
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        VillageLayout.Building b = VillageLayout.placeAt(level, name, VillageLayout.Kind.WORK, HumanJob.FISHERMAN, 0, cx, cz, rot, floorY,
                HumanityManager.occupied(level, s, lay, null), List.of(), 1);
        if (b == null || VillageBuilder.artificial(level, b, 3) > 3) return;
        int[] box = {b.minX() - 3, b.minZ() - 3, b.maxX() + 3, b.maxZ() + 3};
        VillageBuilder.clearTrees(level, box, List.of(b), List.of(), false);
        VillageBuilder.yard(level, b, box, List.of(), HumanityManager.occupied(level, s, lay, null), desert, false);
        VillageBuilder.prepare(level, level, b, box, desert);
        VillageBuilder.place(level, level, b, box, 0, Integer.MAX_VALUE);
        s.blocked.add(b);   // ocupa su lugar para los lotes futuros
    }

    /** Camino nivelado desde el muelle hasta la calle pavimentada más cercana (se suma a la red). */
    private static void road(ServerLevel level, Settlement s, int[] g, boolean desert) {
        VillageLayout.Layout lay = VillageLayout.get(level, s.site());
        HumanityManager.Net nn = HumanityManager.net(s, lay);
        int lx = g[0] - s.portDX * 2, lz = g[1] - s.portDZ * 2;
        double best = Double.MAX_VALUE;
        int bi = -1;
        double tx = s.x, tz = s.z;
        for (int k = 0; k < nn.size(); k++) {
            if (!s.paved[k]) continue;
            VillageLayout.Road r = nn.roads().get(k);
            double t = r.t(lx, lz);
            double qx = r.x0() + (r.x1() - r.x0()) * t, qz = r.z0() + (r.z1() - r.z0()) * t;
            double d = Math.hypot(qx - lx, qz - lz);
            if (d < best) {
                best = d;
                bi = k;
                tx = qx;
                tz = qz;
            }
        }
        if (best > 160) return;
        StreetPlanner.Terrain t = VillageLayout.terrain(level);
        VillageLayout.Road road = new VillageLayout.Road(tx, tz, lx, lz, 1.2, t.height((int) Math.round(tx), (int) Math.round(tz)), g[3]);
        s.extraNet.add(road);
        s.extraParent = java.util.Arrays.copyOf(s.extraParent, s.extraNet.size());
        s.extraParent[s.extraNet.size() - 1] = bi;
        s.extraDist = java.util.Arrays.copyOf(s.extraDist, s.extraNet.size());
        s.extraDist[s.extraNet.size() - 1] = (bi >= 0 ? nn.dist()[bi] : 0) + best;
        HumanityManager.Net after = HumanityManager.net(s, lay);
        s.paved[after.size() - 1] = true;
        int[] box = {(int) Math.min(tx, lx) - 4, (int) Math.min(tz, lz) - 4, (int) Math.max(tx, lx) + 4, (int) Math.max(tz, lz) + 4};
        VillageBuilder.clearTrees(level, box, List.of(), List.of(road), false);
        VillageBuilder.pave(level, box, List.of(road), HumanityManager.built(level, s), desert, false, s.streetTier >= 1);
    }

    private static void lighthouse(ServerLevel level, Settlement s, int[] g) {
        int x0 = g[0] + s.portDX * g[2], z0 = g[1] + s.portDZ * g[2];
        int y = g[3];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState body = Blocks.STONE_BRICKS.defaultBlockState(), white = Blocks.WHITE_CONCRETE.defaultBlockState();
        for (int yy = y + 1; yy <= y + 12; yy++) {
            for (int x = x0 - 1; x <= x0 + 1; x++) {
                for (int z = z0 - 1; z <= z0 + 1; z++) {
                    boolean ring = x != x0 || z != z0;
                    BlockState st = ring ? ((yy - y) % 4 < 2 ? white : body) : Blocks.AIR.defaultBlockState();
                    if (yy == y + 1 && !ring) st = body;
                    level.setBlock(p.set(x, yy, z), st, VillageBuilder.FLAGS);
                }
            }
        }
        for (int x = x0 - 1; x <= x0 + 1; x++) {
            for (int z = z0 - 1; z <= z0 + 1; z++) {
                level.setBlock(p.set(x, y + 13, z), body, VillageBuilder.FLAGS);
                level.setBlock(p.set(x, y + 14, z), (x == x0 && z == z0) ? Blocks.GLOWSTONE.defaultBlockState()
                        : Blocks.GLASS.defaultBlockState(), VillageBuilder.FLAGS);
                level.setBlock(p.set(x, y + 15, z), Blocks.STONE_BRICK_SLAB.defaultBlockState(), VillageBuilder.FLAGS);
            }
        }
    }

    /** Para pruebas: carga lo necesario y levanta ya todos los muelles. Devuelve cuántos quedaron en pie. */
    public static int finish(ServerLevel level, HumanityManager.Data data, Settlement s) {
        if (!has(s)) return 0;
        s.portPiers = wantPiers(s);
        for (int i = 0; i < MAX_PIERS; i++) {
            int[] g = pier(level, s, i);
            if (g == null) continue;
            int[] b = box(s, g);
            for (int cx = b[0] >> 4; cx <= b[2] >> 4; cx++) for (int cz = b[1] >> 4; cz <= b[3] >> 4; cz++) level.getChunk(cx, cz);
        }
        for (int k = 0; k < 20; k++) build(level, data, s);
        int n = 0;
        for (int i = 0; i < s.portPiers && i < s.pierDone.length; i++) if (s.pierDone[i] && pier(level, s, i) != null) n++;
        return n;
    }

    /** Tamaño (en celdas, con tope) del cuerpo de agua que contiene (x, z); marca sus celdas como visitadas. */
    private static int flood(StreetPlanner.Terrain t, int x0, int z0, Set<Long> visited) {
        ArrayDeque<long[]> q = new ArrayDeque<>();
        q.add(new long[]{x0, z0});
        visited.add(key(x0, z0));
        int size = 0;
        while (!q.isEmpty() && size < 1500) {
            long[] c = q.poll();
            size++;
            for (int[] d : new int[][]{{CELL, 0}, {-CELL, 0}, {0, CELL}, {0, -CELL}}) {
                int x = (int) c[0] + d[0], z = (int) c[1] + d[1];
                if (Math.abs(x - x0) > 320 || Math.abs(z - z0) > 320) continue;
                if (!visited.add(key(x, z))) continue;
                if (!t.wet(x, z)) {
                    visited.remove(key(x, z));   // lo seco puede ser orilla de otro cuerpo de agua
                    continue;
                }
                q.add(new long[]{x, z});
            }
        }
        return size;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}

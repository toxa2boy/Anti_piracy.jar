package net.mcreator.antipiracyjar;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber
public class StoneBedsEvent {

    private static final double BED_CHANCE = 0.12;
    private static final int MIN_FREE_CELLS = 180;
    private static final int MIN_CLEARANCE = 4;
    private static final double HIDDEN_CHANCE = 0.55;
    private static final Set<Integer> SKIP_KINDS = Set.of(0, 3, 14, 18, 40, 44);
    private static final double MOUTH_RADIUS = 0.8;
    private static final int MOUTH_GROW = 2;
    private static final boolean DEBUG = true;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CELL = 48;
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private record PlanKey(int cx, int cz, long seed) { }

    private record Plan(BlockPos foot, BlockPos head, Direction facing, boolean hidden, int kind) { }

    private static final Map<PlanKey, Optional<Plan>> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PlanKey, Optional<Plan>> eldest) {
            return size() > 256;
        }
    };

    private static void dbg(String message, Object... args) {
        if (DEBUG) {
            LOGGER.info("[StoneBeds] " + message, args);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof StoneWorldGenerator)) return;

        ChunkAccess chunk = event.getChunk();
        ChunkPos cp = chunk.getPos();
        int chunkX = cp.getMinBlockX() >> 4;
        int chunkZ = cp.getMinBlockZ() >> 4;
        long seed = level.getSeed();

        int minCx = Math.floorDiv(cp.getMinBlockX() - 16, CELL), maxCx = Math.floorDiv(cp.getMaxBlockX() + 16, CELL);
        int minCz = Math.floorDiv(cp.getMinBlockZ() - 16, CELL), maxCz = Math.floorDiv(cp.getMaxBlockZ() + 16, CELL);

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Plan plan = plan(cx, cz, seed);
                if (plan == null) continue;
                if ((plan.foot().getX() >> 4) != chunkX || (plan.foot().getZ() >> 4) != chunkZ) continue;
                place(chunk, plan);
            }
        }
    }

    private static void place(ChunkAccess chunk, Plan plan) {
        BlockPos foot = plan.foot(), head = plan.head();

        if (!chunk.getBlockState(foot).isAir() || !chunk.getBlockState(head).isAir()
                || !chunk.getBlockState(foot.below()).canOcclude() || !chunk.getBlockState(head.below()).canOcclude()) {
            dbg("plan at {} rejected: the real chunk differs from the layout", foot);
            return;
        }

        BlockState footState = StoneWorldGenerator.Column.bed(false).setValue(BlockStateProperties.HORIZONTAL_FACING, plan.facing());
        BlockState headState = StoneWorldGenerator.Column.bed(true).setValue(BlockStateProperties.HORIZONTAL_FACING, plan.facing());
        chunk.setBlockState(foot, footState, false);
        chunk.setBlockState(head, headState, false);
        dbg("bed placed at {} {} {} ({}), room kind {}", foot.getX(), foot.getY(), foot.getZ(),
                plan.hidden() ? "hidden spot" : "open spot", plan.kind());
    }

    private static Plan plan(int cx, int cz, long seed) {
        PlanKey key = new PlanKey(cx, cz, seed);
        synchronized (CACHE) {
            Optional<Plan> cached = CACHE.get(key);
            if (cached != null) return cached.orElse(null);
        }
        Plan plan = compute(cx, cz, seed);
        synchronized (CACHE) {
            CACHE.put(key, Optional.ofNullable(plan));
        }
        return plan;
    }

    private static double fraction(long h) {
        return (h >>> 11) / (double) (1L << 53);
    }

    private static boolean same(StoneWorldLayout.Room a, StoneWorldLayout.Room b) {
        return a != null && a.x == b.x && a.z == b.z;
    }

    private static boolean isFree(StoneWorldLayout.Sample s, StoneWorldLayout.Room room) {
        if (!same(s.room, room)) return false;
        if (s.wallRoom != null) return false;
        for (StoneWorldLayout.Point p : s.tunnels) {
            if (p.main() && p.floor() != room.floor) return false;
        }
        if (s.top < room.floor + 3) return false;
        BlockState floor = s.at(room.floor);
        if (floor.isAir() || !floor.getFluidState().isEmpty() || !floor.canOcclude()) return false;
        return s.at(room.floor + 1).isAir() && s.at(room.floor + 2).isAir();
    }

    private static boolean isMouth(StoneWorldLayout.Sample s, StoneWorldLayout.Room room) {
        for (StoneWorldLayout.Point p : s.tunnels) {
            if (p.main() && room.radius(p.x(), p.z()) > MOUTH_RADIUS) return true;
        }
        for (StoneWorldLayout.Point p : s.shoulders) {
            if (p.main() && room.radius(p.x(), p.z()) > MOUTH_RADIUS) return true;
        }
        return false;
    }

    private static Plan compute(int cx, int cz, long seed) {
        if (cx == 0 && cz == 0) return null;
        StoneWorldLayout.Room room = new StoneWorldLayout.Room(cx, cz, seed);
        if (SKIP_KINDS.contains(room.kind)) return null;

        long h = StoneWorldLayout.hash(cx, cz, seed + 90210L);
        if (fraction(h) >= BED_CHANCE) return null;

        int minX = room.x - room.width - 2;
        int minZ = room.z - room.depth - 2;
        int w = 2 * room.width + 5;
        int d = 2 * room.depth + 5;
        boolean[][] free = new boolean[w][d];
        boolean[][] mouth = new boolean[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                StoneWorldLayout.Sample s = StoneWorldLayout.column(minX + i, minZ + j, seed);
                free[i][j] = isFree(s, room);
                mouth[i][j] = isMouth(s, room);
            }
        }
        for (int g = 0; g < MOUTH_GROW; g++) {
            boolean[][] grown = new boolean[w][d];
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < d; j++) {
                    if (!mouth[i][j]) continue;
                    for (int di = -1; di <= 1; di++) {
                        for (int dj = -1; dj <= 1; dj++) {
                            int ni = i + di, nj = j + dj;
                            if (ni >= 0 && nj >= 0 && ni < w && nj < d) grown[ni][nj] = true;
                        }
                    }
                }
            }
            mouth = grown;
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (mouth[i][j]) free[i][j] = false;
            }
        }

        int[][] clr = clearance(free, w, d);
        int bi = -1, bj = -1, best = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (clr[i][j] > best) {
                    best = clr[i][j];
                    bi = i;
                    bj = j;
                }
            }
        }
        if (bi < 0) return null;
        boolean[][] reach = flood(free, w, d, bi, bj);
        clr = clearance(reach, w, d);
        best = 0;
        int count = 0;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (reach[i][j]) count++;
                best = Math.max(best, clr[i][j]);
            }
        }

        if (count < MIN_FREE_CELLS || best < MIN_CLEARANCE) {
            dbg("room {},{} kind {} skipped: too small or cramped (free={}, clearance={})", cx, cz, room.kind, count, best);
            return null;
        }

        boolean hidden = fraction(StoneWorldLayout.hash(cx, cz, seed + 4242L)) < HIDDEN_CHANCE;

        List<int[]> candidates = new ArrayList<>();
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (!reach[i][j]) continue;
                for (int k = 0; k < DIRS.length; k++) {
                    int ni = i + DIRS[k].getStepX(), nj = j + DIRS[k].getStepZ();
                    if (ni < 0 || nj < 0 || ni >= w || nj >= d || !reach[ni][nj]) continue;

                    int fx = minX + i, fz = minZ + j, hx = minX + ni, hz = minZ + nj;
                    if ((fx >> 4) != (hx >> 4) || (fz >> 4) != (hz >> 4)) continue;

                    int score;
                    if (hidden) {
                        int blocked = blockedAround(reach, w, d, i, j, ni, nj);
                        if (blocked > 8) continue;
                        score = blocked;
                        if (!isReach(reach, w, d, ni + DIRS[k].getStepX(), nj + DIRS[k].getStepZ())) score += 3;
                    } else {
                        score = Math.min(clr[i][j], clr[ni][nj]);
                    }
                    candidates.add(new int[]{i, j, k, score});
                    top = Math.max(top, score);
                }
            }
        }

        List<int[]> finalists = new ArrayList<>();
        for (int[] c : candidates) {
            if (c[3] >= top - 1) finalists.add(c);
        }
        if (finalists.isEmpty()) return null;
        int[] pick = finalists.get(StoneWorldLayout.number(StoneWorldLayout.hash(cx, cz, seed + 777L), 20, finalists.size()));

        Direction facing = DIRS[pick[2]];
        BlockPos foot = new BlockPos(minX + pick[0], room.floor + 1, minZ + pick[1]);
        BlockPos head = foot.relative(facing);
        return new Plan(foot, head, facing, hidden, room.kind);
    }

    private static boolean isReach(boolean[][] g, int w, int d, int i, int j) {
        return i >= 0 && j >= 0 && i < w && j < d && g[i][j];
    }

    private static int blockedAround(boolean[][] g, int w, int d, int i1, int j1, int i2, int j2) {
        int blocked = 0;
        for (int i = Math.min(i1, i2) - 1; i <= Math.max(i1, i2) + 1; i++) {
            for (int j = Math.min(j1, j2) - 1; j <= Math.max(j1, j2) + 1; j++) {
                if ((i == i1 && j == j1) || (i == i2 && j == j2)) continue;
                if (!isReach(g, w, d, i, j)) blocked++;
            }
        }
        return blocked;
    }

    private static int[][] clearance(boolean[][] free, int w, int d) {
        int[][] c = new int[w][d];
        final int INF = 10000;
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                if (!free[i][j]) {
                    c[i][j] = 0;
                    continue;
                }
                int v = INF;
                v = Math.min(v, at(c, w, d, i - 1, j - 1) + 1);
                v = Math.min(v, at(c, w, d, i - 1, j) + 1);
                v = Math.min(v, at(c, w, d, i - 1, j + 1) + 1);
                v = Math.min(v, at(c, w, d, i, j - 1) + 1);
                c[i][j] = v;
            }
        }
        for (int i = w - 1; i >= 0; i--) {
            for (int j = d - 1; j >= 0; j--) {
                if (!free[i][j]) continue;
                int v = c[i][j];
                v = Math.min(v, at(c, w, d, i + 1, j + 1) + 1);
                v = Math.min(v, at(c, w, d, i + 1, j) + 1);
                v = Math.min(v, at(c, w, d, i + 1, j - 1) + 1);
                v = Math.min(v, at(c, w, d, i, j + 1) + 1);
                c[i][j] = v;
            }
        }
        return c;
    }

    private static int at(int[][] c, int w, int d, int i, int j) {
        return (i < 0 || j < 0 || i >= w || j >= d) ? 0 : c[i][j];
    }

    private static boolean[][] flood(boolean[][] free, int w, int d, int si, int sj) {
        boolean[][] seen = new boolean[w][d];
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[]{si, sj});
        seen[si][sj] = true;
        while (!stack.isEmpty()) {
            int[] p = stack.remove(stack.size() - 1);
            for (Direction dir : DIRS) {
                int ni = p[0] + dir.getStepX(), nj = p[1] + dir.getStepZ();
                if (ni < 0 || nj < 0 || ni >= w || nj >= d) continue;
                if (!free[ni][nj] || seen[ni][nj]) continue;
                seen[ni][nj] = true;
                stack.add(new int[]{ni, nj});
            }
        }
        return seen;
    }
}
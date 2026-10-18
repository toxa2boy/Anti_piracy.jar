package net.mcreator.antipiracyjar;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

final class StoneWorldLayout {
    static final int VARIANTS = 40;
    private record Key(int x, int z, long seed) { }
    private static final Map<Key, Region> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);

    static long hash(int x, int z, long seed) { return StoneWorldGenerator.hash(x, z, seed); }
    static int number(long h, int shift, int count) { return (int) Math.floorMod(h >>> shift, count); }

    static Sample column(int x, int z, long seed) {
        Key key = new Key(Math.floorDiv(x, 48), Math.floorDiv(z, 48), seed);
        Region region;
        synchronized (CACHE) {
            region = CACHE.get(key);
            if (region == null) {
                region = new Region(key);
                CACHE.put(key, region);
                if (CACHE.size() > 128) CACHE.remove(CACHE.keySet().iterator().next());
            }
        }
        return new Sample(x, z, seed, region);
    }

    static final class Room {
        final int x, z, floor, width, depth, height, kind, rotation, shape;
        final long salt;
        Room(int cx, int cz, long seed) {
            salt = hash(cx, cz, seed);
            boolean start = cx == 0 && cz == 0;
            x = cx * 48 + 24 + (start ? 0 : number(salt, 8, 17) - 8);
            z = cz * 48 + 24 + (start ? 0 : number(salt, 17, 17) - 8);
            floor = start ? 40 : 36 + number(salt, 27, 9);
            kind = start ? 0 : number(salt, 0, VARIANTS);
            width = start ? 11 : 6 + number(salt, 35, 10);
            depth = start ? 12 : 6 + number(salt, 44, 10);
            height = start ? 8 : switch (kind) {
                case 0, 1, 2, 5, 6, 7, 14, 19, 20, 24 -> 6 + number(salt, 51, 7);
                default -> 3 + number(salt, 51, 8);
            };
            rotation = start ? 0 : number(salt, 24, 4);
            shape = start ? 0 : number(salt, 31, 5);
        }

        double radius(int wx, int wz) {
            double dx = (wx - x) / (double) width, dz = (wz - z) / (double) depth;
            double wobble = 0.11 * Math.sin(wx * 0.67 + (salt & 31)) * Math.cos(wz * 0.51);
            return switch (shape) {
                case 0 -> Math.max(Math.abs(dx), Math.abs(dz)) + wobble * 0.3;
                case 1 -> Math.sqrt(dx * dx + dz * dz) + wobble;
                case 2 -> Math.pow(Math.pow(Math.abs(dx), 4) + Math.pow(Math.abs(dz), 4), 0.25) + wobble;
                case 3 -> Math.min(Math.hypot(dx + 0.28, dz), Math.hypot(dx - 0.30, dz + 0.25)) + wobble;
                default -> Math.min(Math.max(Math.abs(dx), Math.abs(dz) * 2.1), Math.max(Math.abs(dx) * 2.2, Math.abs(dz))) + wobble;
            };
        }
    }

    record Point(int x, int z, int floor, double radius, int height, boolean main) { }

    static final class Region {
        final List<Room> rooms = new ArrayList<>();
        final List<Point> points = new ArrayList<>();
        Region(Key key) {
            for (int cx = key.x - 1; cx <= key.x + 1; cx++) {
                for (int cz = key.z - 1; cz <= key.z + 1; cz++) {
                    Room a = new Room(cx, cz, key.seed);
                    rooms.add(a);
                    route(a, new Room(cx + 1, cz, key.seed), hash(cx, cz, key.seed + 913), true);
                    if (Math.floorMod(cx, 3) == 0 || (hash(cx, cz, key.seed + 71) & 3) == 0)
                        route(a, new Room(cx, cz + 1, key.seed), hash(cx, cz, key.seed + 317), true);
                    long h = hash(cx, cz, key.seed + 819);
                    int length = 12 + number(h, 8, 20);
                    int dx = number(h, 13, 2) == 0 ? length : -length;
                    branch(a, a.x + dx, a.z + number(h, 24, 31) - 15, a.floor - number(h, 34, 5), h);
                }
            }
        }

        void route(Room a, Room b, long salt, boolean main) {
            path(a.x, a.z, a.floor, b.x, b.z, b.floor, salt, main);
        }

        void branch(Room a, int x, int z, int floor, long salt) {
            path(a.x, a.z, a.floor, x, z, floor, salt, false);
        }

        void path(int ax, int az, int af, int bx, int bz, int bf, long salt, boolean main) {
            double length = Math.hypot(bx - ax, bz - az);
            int steps = Math.max(1, (int) (length * 3));
            int px = ax, pz = az, pf = af;
            double bend = 3 + number(salt, 8, 7);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double wave = Math.sin(t * Math.PI) * (Math.sin(t * Math.PI * 3 + (salt & 15)) * bend);
                int x = (int) Math.round(ax + (bx - ax) * t - (bz - az) / length * wave);
                int z = (int) Math.round(az + (bz - az) * t + (bx - ax) / length * wave);
                int floor = (int) Math.round(af + (bf - af) * t);
                double radius = main ? (0.32 + 0.9 * (0.5 + 0.5 * Math.sin(t * 17 + (salt & 31)))) : 0.55;
                double roofWave = 0.5 + 0.5 * Math.sin(t * 23 + number(salt, 20, 19));
                int height = main ? 2 + (roofWave > 0.7 ? 1 : 0) : (t > 0.6 ? 1 : 2 + (int) (roofWave * 2));
                while (px != x || pz != z) {
                    if (px != x) px += Integer.signum(x - px); else pz += Integer.signum(z - pz);
                    pf += Integer.signum(floor - pf);
                    points.add(new Point(px, pz, pf, radius, height, main));
                }
                points.add(new Point(x, z, floor, radius, height, main));
                pf = floor;
            }
        }
    }

    static final class Sample {
        final int wx, wz, x, z, bottom, top, kind;
        final long seed;
        final Room room;
        final List<Point> tunnels = new ArrayList<>();
        final List<Point> shoulders = new ArrayList<>();
        final long texture;
        final boolean centerline;

        Sample(int wx, int wz, long seed, Region region) {
            this.wx = wx; this.wz = wz; this.seed = seed;
            texture = hash(wx, wz, seed + 437);
            Room chosen = null;
            double best = 10;
            for (Room r : region.rooms) {
                double radius = r.radius(wx, wz);
                if (radius <= 1 && radius < best) { chosen = r; best = radius; }
            }
            room = chosen;
            kind = room == null ? -1 : room.kind;
            int dx = room == null ? 0 : wx - room.x, dz = room == null ? 0 : wz - room.z;
            int rotation = room == null ? 0 : room.rotation;
            x = rotation == 0 ? dx : rotation == 1 ? dz : rotation == 2 ? -dx : -dz;
            z = rotation == 0 ? dz : rotation == 1 ? -dx : rotation == 2 ? -dz : dx;
            bottom = room == null ? 40 : room.floor;
            top = room == null ? 40 : room.floor + Math.max(3, room.height - (int) (best * best * (room.shape == 0 ? 0 : 4))
                    + (int) Math.round(Math.sin(wx * .37) * Math.cos(wz * .31)));
            boolean center = false;
            for (Point p : region.points) {
                int tx = wx - p.x, tz = wz - p.z;
                if (tx * tx + tz * tz <= p.radius * p.radius) tunnels.add(p);
                else if (tx * tx + tz * tz <= (p.radius + 1.15) * (p.radius + 1.15)) shoulders.add(p);
                if (p.main && tx == 0 && tz == 0) center = true;
            }
            centerline = center;
        }

        BlockState at(int y) {
            if (y <= -64 || y >= 319) return Blocks.BEDROCK.defaultBlockState();
            if (y < 24 || y > 70) return STONE;
            int tunnelFloor = Integer.MAX_VALUE, tunnelTop = Integer.MIN_VALUE;
            boolean main = false;
            for (Point p : tunnels) main |= p.main;
            for (Point p : tunnels) {
                if (main && !p.main) continue;
                tunnelFloor = Math.min(tunnelFloor, p.floor);
                tunnelTop = Math.max(tunnelTop, p.floor + p.height);
            }
            int tunnelProfile = number(hash(Math.floorDiv(wx, 6), Math.floorDiv(wz, 6), seed + 2081), 12, 3);
            boolean steps = false;
            for (Point p : tunnels) if (p.main && p.floor != tunnelFloor) steps = true;
            if (main && room == null) tunnelTop = tunnelFloor + (steps || tunnelProfile == 2 ? 3 : 2);
            if (main && y == tunnelFloor) return STONE;
            if (tunnelFloor != Integer.MAX_VALUE && y > tunnelFloor && y <= tunnelTop) {
                if (main || room == null) {
                    if (room == null) {
                        int clearance = tunnelTop - tunnelFloor;
                        if (y == tunnelTop && clearance == 2 && tunnelProfile == 0 && !steps)
                            return Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
                        if (y == tunnelTop && clearance >= 3 && !centerline) {
                            int roof = number(texture, 8, 6);
                            if (roof == 0) return STONE;
                            if (roof == 1 || roof == 2) return Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
                            if (roof == 3) return Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                                    .setValue(BlockStateProperties.HALF, Half.TOP)
                                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(number(texture, 17, 4)));
                        }
                        if (!centerline && y == tunnelFloor + 1) {
                            if ((texture & 7) == 0) return Blocks.COBBLESTONE_WALL.defaultBlockState();
                            if ((texture & 7) == 1) return Blocks.STONE_SLAB.defaultBlockState();
                            if ((texture & 7) == 2) return Blocks.ANDESITE_STAIRS.defaultBlockState()
                                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(number(texture, 17, 4)));
                        }
                    }
                    return AIR;
                }
            }
            if (room == null) {
                if (!shoulders.isEmpty()) {
                    int base = shoulders.get(0).floor;
                    int ridge = number(texture, 12, 8);
                    if (y == base + 1 && ridge <= 2) return ridge == 0 ? AIR : ridge == 1
                            ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.STONE_SLAB.defaultBlockState();
                    if (y == base + 2 && ridge <= 2) return AIR;
                    if (y == base + 3 && ridge == 0) return Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
                    if (y >= base && y <= base + 5 && ridge == 4) return Blocks.ANDESITE.defaultBlockState();
                    if (y >= base && y <= base + 5 && ridge == 5) return Blocks.COBBLESTONE.defaultBlockState();
                }
                return STONE;
            }
            if (y < bottom || y > top) return STONE;
            if (y == bottom) return floor();
            BlockState detail = decorate(y - bottom);
            if (detail != null) return detail.rotate(switch (room.rotation) {
                case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
                case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
                case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                default -> net.minecraft.world.level.block.Rotation.NONE;
            });
            if (!main && room.shape != 0 && y == top && (hash(wx, wz, seed) & 15) == 0) return STONE;
            return AIR;
        }

        BlockState floor() {
            if (kind == 0 && Math.abs(x) <= 4 && Math.abs(z + 5) <= 3) return Blocks.OAK_PLANKS.defaultBlockState();
            return switch (kind) {
                case 0, 1, 2, 6, 7, 9, 14, 20 -> Blocks.GRASS_BLOCK.defaultBlockState();
                case 3, 8, 11, 18 -> Blocks.OAK_PLANKS.defaultBlockState();
                case 12, 16, 27, 36 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                case 25, 29, 30 -> Blocks.OAK_PLANKS.defaultBlockState();
                case 24 -> Blocks.MYCELIUM.defaultBlockState();
                case 34 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
                case 17 -> Blocks.SAND.defaultBlockState();
                case 19, 37 -> Blocks.GRAVEL.defaultBlockState();
                default -> STONE;
            };
        }

        BlockState decorate(int y) {
            int h = top - bottom;
            long noise = hash(wx, wz, seed + room.kind);
            switch (kind) {
                case 0, 14 -> {
                    BlockState house = StoneWorldGenerator.Column.house(x, z + 5, y);
                    if (house != null) return house;
                    if (x == -5 && z == 3 && y <= 3) return Blocks.OAK_FENCE.defaultBlockState();
                    if (x == -5 && z == 3 && y == 4) return Blocks.TORCH.defaultBlockState();
                    if (kind == 14 && x > 4 && y <= 2 && (noise & 3) == 0) return Blocks.COBBLESTONE.defaultBlockState();
                }
                case 1, 20 -> {
                    for (int[] p : TREES) {
                        BlockState tree = tree(x - p[0], y, z - p[1], 6, Direction.Axis.Y);
                        if (tree != null) return tree;
                    }
                    if (kind == 20 && y == 1 && (noise & 7) == 0) return Blocks.MOSS_CARPET.defaultBlockState();
                }
                case 2 -> {
                    int dx = x - 3, dz = z - 3, r = Math.max(Math.abs(dx), Math.abs(dz));
                    if (y == 1 && r <= 3 || y == 2 && r == 2 || y == 5 && r <= 3) return Blocks.COBBLESTONE.defaultBlockState();
                    if (y == 2 && r < 2) return Blocks.WATER.defaultBlockState();
                    if ((y == 3 || y == 4) && Math.abs(dx) == 2 && Math.abs(dz) == 2) return Blocks.OAK_FENCE.defaultBlockState();
                }
                case 3, 18 -> {
                    if (Math.abs(x) >= 3 && z <= -2 && y == 4) return Blocks.OAK_PLANKS.defaultBlockState();
                    if (Math.abs(x) >= 3 && z == -2 && y <= 3 && Math.floorMod(x, 5) != 0) return Blocks.COBBLESTONE.defaultBlockState();
                    if (y == 1 && Math.floorMod(x, 4) == 0 && (z == -4 || z == -5)) return StoneWorldGenerator.Column.bed(z == -5);
                    if (kind == 18 && y <= 3 && x == 4 && z > 2) return Blocks.BOOKSHELF.defaultBlockState();
                }
                case 4 -> {
                    if (Math.abs(x) == 4 && Math.abs(z) == 4 && y < h) return Blocks.STONE_BRICKS.defaultBlockState();
                }
                case 5, 19 -> {
                    if (Math.floorMod(z, 6) == 0 && Math.abs(x) <= 4 && (y == 5 || Math.abs(x) == 4 && y < 5))
                        return (y == 5 ? Blocks.OAK_PLANKS : Blocks.OAK_FENCE).defaultBlockState();
                    if (x == 2 && y == 1 && (noise & 7) != 0) return Blocks.RAIL.defaultBlockState();
                    if (kind == 19 && y <= 1 + (noise & 3) && Math.abs(x) > 3) return Blocks.GRAVEL.defaultBlockState();
                }
                case 6 -> {
                    BlockState t = tree(x - 3, room.height + 1 - y, z - 2, 6, Direction.Axis.Y);
                    if (t != null) return t;
                    if (Math.abs(x + 4) <= 1 && Math.abs(z + 3) <= 1 && y >= h - 1) return Blocks.DIRT.defaultBlockState();
                    t = tree(x + 4, room.height - y, z + 3, 5, Direction.Axis.Y);
                    if (t != null) return t;
                }
                case 7 -> {
                    BlockState t = tree(y - 4, room.width + x, z - 2, 7, Direction.Axis.X);
                    if (t != null) return t;
                    t = tree(y - 6, room.depth - z, x + 2, 6, Direction.Axis.Z);
                    if (t != null) return t;
                }
                case 8 -> {
                    if (x >= 3 && y <= 2 && Math.floorMod(z, 4) == 0) return Blocks.OAK_DOOR.defaultBlockState()
                            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y % 2 == 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
                    if (y == 5 && x > 2) return Blocks.OAK_PLANKS.defaultBlockState();
                }
                case 9 -> {
                    if (Math.abs(x) > 2 && Math.abs(z) > 2 && y == 1) return Blocks.FARMLAND.defaultBlockState();
                    if (Math.abs(x) > 2 && Math.abs(z) > 2 && y == 2 && (noise & 3) != 0) return Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, number(noise, 4, 8));
                }
                case 10 -> {
                    if (Math.abs(x) <= 3 && z > 1 && y <= Math.min(7, z - 1)) return Blocks.COBBLESTONE.defaultBlockState();
                }
                case 11 -> {
                    if (y == 1 && z > 2 && Math.floorMod(x, 3) == 0) return Blocks.CRAFTING_TABLE.defaultBlockState();
                    if (y == 1 && z < -3 && Math.floorMod(x, 3) == 0) return Blocks.FURNACE.defaultBlockState();
                }
                case 12 -> {
                    if (Math.abs(x - 3) <= 3 && Math.abs(z - 3) <= 3 && y == 1) return Blocks.COBBLESTONE.defaultBlockState();
                    if (Math.abs(x - 3) <= 2 && Math.abs(z - 3) <= 2 && y == 2) return Blocks.WATER.defaultBlockState();
                    if ((Math.abs(x - 3) == 3 && Math.abs(z - 3) <= 3 || Math.abs(z - 3) == 3 && Math.abs(x - 3) <= 3) && y == 2) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
                case 13 -> {
                    if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && y <= 3 + number(noise, 7, 5)) return STONE;
                    if (y >= h - 2 && (noise & 7) == 0) return STONE;
                }
                case 15 -> {
                    if (x == -3 && y <= h - 1 && Math.abs(z) < 4) return Blocks.STONE_BRICKS.defaultBlockState();
                    if (x == -2 && Math.abs(z) < 4 && y == 3) return Blocks.GLASS.defaultBlockState();
                    if (x == 4 && y < 4 && Math.abs(z) < 4) return Blocks.BRICKS.defaultBlockState();
                }
                case 16 -> {
                    if (Math.abs(x) >= 4 && y <= 2 && Math.floorMod(z, 3) == 0) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    if (y == 1 && (noise & 15) == 0) return Blocks.RED_MUSHROOM.defaultBlockState();
                }
                case 17 -> {
                    if (Math.abs(x) > 3 && y <= 1 + number(noise, 7, 3)) return Blocks.SANDSTONE.defaultBlockState();
                }
                case 21 -> {
                    if (x > 2 && Math.abs(z) <= 4 && y == h - 2) return Blocks.OAK_PLANKS.defaultBlockState();
                    if (x == 4 && (z == -3 || z == 3) && y >= h - 1) return Blocks.OAK_FENCE.defaultBlockState();
                    if (x == 5 && z == 0 && y == h - 1) return Blocks.CRAFTING_TABLE.defaultBlockState();
                }
                case 22 -> {
                    if (Math.abs(x) > 3 && Math.abs(z) > 2 && y <= 1 + number(noise, 3, 4)) return Blocks.COBBLESTONE.defaultBlockState();
                    if (y == h && (noise & 3) == 0) return Blocks.COBWEB.defaultBlockState();
                }
                case 23 -> {
                    if (Math.abs(x) > 2 && y <= 2 && Math.floorMod(z, 4) == 0) return Blocks.STONE_BRICKS.defaultBlockState();
                    if (y == 3 && Math.abs(x) == 4 && Math.floorMod(z, 4) == 0) return Blocks.TORCH.defaultBlockState();
                }
                case 24 -> {
                    for (int[] p : TREES) {
                        int dx = x - p[0], dz = z - p[1];
                        if (dx == 0 && dz == 0 && y <= 4) return Blocks.MUSHROOM_STEM.defaultBlockState();
                        if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && y == 5) return Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
                    }
                    if (y == 1 && (noise & 7) == 0) return Blocks.BROWN_MUSHROOM.defaultBlockState();
                }
                case 25 -> {
                    if (Math.abs(x) >= 3 && Math.floorMod(z, 4) == 0 && y <= 4) return Blocks.BOOKSHELF.defaultBlockState();
                    if (Math.abs(x) == 4 && z == 2 && y == 1) return Blocks.LECTERN.defaultBlockState();
                }
                case 26 -> {
                    if (Math.floorMod(x, 5) == 0 && Math.abs(z) >= 3 && (y <= 4 || y == 5 && Math.abs(z) < 6)) return Blocks.STONE_BRICKS.defaultBlockState();
                    if (y == 5 && Math.abs(z) == 3) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);
                }
                case 27 -> {
                    if (Math.abs(x) >= 3 && Math.abs(x) <= 5 && y == 1) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                    if (Math.abs(x) == 3 || Math.abs(x) == 5) {
                        if (y == 2) return Blocks.COBBLESTONE_WALL.defaultBlockState();
                    }
                    if (Math.abs(x) == 4 && y == 2) return Blocks.WATER.defaultBlockState();
                }
                case 28 -> {
                    if (x >= 3 && x <= 6 && z >= 2 && z <= 6) {
                        if (y == 4) return Blocks.STONE_SLAB.defaultBlockState();
                        if (y <= 3 && (x == 3 || x == 6 || z == 2 || z == 6)) return Blocks.IRON_BARS.defaultBlockState();
                    }
                }
                case 29 -> {
                    if (Math.abs(x) > 3 && Math.floorMod(z, 3) == 0 && y <= 3) return Blocks.BARREL.defaultBlockState();
                    if (Math.abs(x) > 3 && y == 4) return Blocks.OAK_SLAB.defaultBlockState();
                }
                case 30 -> {
                    if (y >= h - 3 && Math.floorMod(x, 4) == 0 && Math.abs(z) > 2) return Blocks.CHAIN.defaultBlockState();
                    if (y == h - 4 && Math.floorMod(x, 4) == 0 && Math.abs(z) > 2) return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
                }
                case 31 -> {
                    if (y >= 3 && y <= 5 && z == 3 && x >= 2) return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
                    if (y == 5 && x == 4 && Math.abs(z) < 5) return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
                    if (y == 1 && (noise & 15) == 0) return Blocks.DEAD_BUSH.defaultBlockState();
                }
                case 32 -> {
                    int r = Math.max(Math.abs(x - 4), Math.abs(z - 3));
                    if (r <= 3 && y <= 4 - r) return Blocks.STONE_BRICKS.defaultBlockState();
                    if (r == 0 && y == 5) return Blocks.REDSTONE_TORCH.defaultBlockState();
                }
                case 33 -> {
                    if (Math.abs(x) > 3 && y == 1 && Math.floorMod(z, 3) == 0) return Blocks.FURNACE.defaultBlockState();
                    if (Math.abs(x) == 5 && Math.floorMod(z, 3) == 0 && y > 1 && y <= h) return Blocks.COBBLESTONE_WALL.defaultBlockState();
                }
                case 34 -> {
                    if (Math.abs(x) >= 3 && Math.floorMod(z, 4) == 0 && y <= 2) return Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
                    if (Math.abs(x) == 5 && y >= h - 1) return Blocks.IRON_BARS.defaultBlockState();
                }
                case 35 -> {
                    if (x >= 3 && z >= 2 && y <= 3 && (x == 3 || z == 2)) return Blocks.OAK_FENCE.defaultBlockState();
                    if (x > 3 && z > 2 && y == 1 && (noise & 3) == 0) return Blocks.HAY_BLOCK.defaultBlockState();
                }
                case 36 -> {
                    if (Math.abs(x) == 4 && Math.abs(z) >= 2 && y <= h - 2) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                    if (Math.abs(x) > 3 && y == 1 && (noise & 7) == 0) return Blocks.MOSS_CARPET.defaultBlockState();
                    if (Math.abs(x) > 3 && y == h - 1 && (noise & 3) == 0) return LEAVES;
                }
                case 37 -> {
                    if (Math.abs(x) > 2 && Math.abs(z) > 2 && y <= 1 + number(noise, 8, 3)) return Blocks.ANDESITE.defaultBlockState();
                    if (y >= h - 2 && (noise & 15) == 0) return Blocks.COBBLESTONE_WALL.defaultBlockState();
                }
                case 38 -> {
                    if (Math.floorMod(x, 4) == 0 && Math.abs(z) > 3 && y <= 3) return Blocks.STONE_BRICKS.defaultBlockState();
                    if (Math.floorMod(x, 4) == 0 && Math.abs(z) > 3 && y == 4) return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                    if (y == 1 && x == 3 && z > 3) return Blocks.REDSTONE_WIRE.defaultBlockState();
                }
                case 39 -> {
                    int dx = x - 4, dz = z - 3;
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3 && y == Math.abs(dx) + Math.abs(dz) + 1) return Blocks.STONE_SLAB.defaultBlockState();
                    if (Math.abs(x) >= 3 && y == h) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);
                }
            }
            if (y == 1 && x == -4 && z == 3 && kind % 3 != 1) return Blocks.TORCH.defaultBlockState();
            return null;
        }
    }

    private static final int[][] TREES = {{-4,-4},{5,4},{-4,6},{6,-5}};

    private static BlockState tree(int a, int along, int b, int length, Direction.Axis axis) {
        if (along <= 0) return null;
        if (a == 0 && b == 0 && along <= length) return Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
        if (along >= length - 2 && along <= length + 1 && Math.abs(a) <= (along == length + 1 ? 1 : 2) && Math.abs(b) <= (along == length + 1 ? 1 : 2)) return LEAVES;
        return null;
    }
}

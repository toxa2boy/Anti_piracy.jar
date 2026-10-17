package net.mcreator.antipiracyjar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
public final class StoneWorldGenerator extends FlatLevelSource {
    public static final int FLOOR = 40;
    public static final MapCodec<StoneWorldGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(StoneWorldGenerator::settings)
    ).apply(instance, StoneWorldGenerator::new));
    private volatile long worldSeed;

    public StoneWorldGenerator(FlatLevelGeneratorSettings settings) { super(settings); }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.CHUNK_GENERATOR, helper -> helper.register(
                ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "stone_rooms"), CODEC));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        worldSeed = seed;
        return super.createState(structures, random, seed);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Column column = new Column(chunk.getPos().getMinBlockX() + x, chunk.getPos().getMinBlockZ() + z, worldSeed);
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    BlockState state = column.at(y);
                    if (!state.isAir()) chunk.setBlockState(pos.set(x, y, z), state, false);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, java.util.Set.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) { }

    @Override
    public int getMinY() { return -64; }

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) { return FLOOR + 1; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return 320; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        Column column = new Column(x, z, worldSeed);
        for (int i = 0; i < states.length; i++) states[i] = column.at(level.getMinBuildHeight() + i);
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    static long hash(int x, int z, long seed) {
        long value = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static final class Column {
        final int x, z, width, depth, height, kind;
        final boolean room, tunnel, beam, lamp;

        Column(int wx, int wz, long seed) {
            int cx = Math.floorDiv(wx, 48), cz = Math.floorDiv(wz, 48);
            long h = hash(cx, cz, seed);
            x = Math.floorMod(wx, 48) - 24;
            z = Math.floorMod(wz, 48) - 24;
            kind = cx == 0 && cz == 0 ? 0 : (int) Math.floorMod(h, 6);
            width = 8 + (int) ((h >>> 8) % 6);
            depth = 9 + (int) ((h >>> 16) % 6);
            height = kind == 1 ? 12 : 7 + (int) ((h >>> 24) % 5);
            room = Math.abs(x) <= width && Math.abs(z) <= depth;
            int edgeZ = z < 0 ? cz - 1 : cz;
            boolean northSouth = Math.floorMod(cx, 3) == 0 || (hash(cx, edgeZ, seed + 71) & 3) == 0;
            tunnel = Math.abs(z) <= 1 || (northSouth && Math.abs(x) <= 1);
            beam = !room && ((Math.abs(z) <= 1 && Math.floorMod(wx, 9) == 0)
                    || (northSouth && Math.abs(x) <= 1 && Math.floorMod(wz, 9) == 0));
            lamp = !room && Math.abs(z) == 1 && Math.floorMod(wx, 23) == 0;
        }

        BlockState at(int y) {
            if (y <= -64 || y >= 319) return Blocks.BEDROCK.defaultBlockState();
            int dy = y - FLOOR;
            if (dy < 0 || dy > height) return Blocks.STONE.defaultBlockState();
            if (room) {
                if (dy == 0) return (kind == 0 && Math.abs(x) <= 4 && Math.abs(z + 5) <= 3 ? Blocks.OAK_PLANKS
                        : kind <= 2 ? Blocks.GRASS_BLOCK : kind == 3 ? Blocks.OAK_PLANKS : Blocks.STONE).defaultBlockState();
                BlockState detail = decoration(dy);
                return detail == null ? Blocks.AIR.defaultBlockState() : detail;
            }
            if (tunnel && dy > 0 && dy <= 4) {
                if (beam && (dy == 4 || (Math.abs(z) == 1 && Math.abs(x) > width) || (Math.abs(x) == 1 && Math.abs(z) > depth)))
                    return (dy == 4 ? Blocks.OAK_PLANKS : Blocks.OAK_FENCE).defaultBlockState();
                if (lamp && dy == 1) return Blocks.TORCH.defaultBlockState();
                return Blocks.AIR.defaultBlockState();
            }
            return Blocks.STONE.defaultBlockState();
        }

        BlockState decoration(int y) {
            if (kind == 0) {
                BlockState house = house(x, z + 5, y);
                if (house != null) return house;
                if (x == -5 && z == 3 && y <= 3) return Blocks.OAK_FENCE.defaultBlockState();
                if (x == -5 && z == 3 && y == 4) return Blocks.TORCH.defaultBlockState();
            }
            if (kind == 1) {
                for (int[] tree : new int[][] {{-5,-5},{5,4},{-4,6}}) {
                    int dx = x - tree[0], dz = z - tree[1];
                    if (dx == 0 && dz == 0 && y <= 6) return Blocks.OAK_LOG.defaultBlockState();
                    if (y >= 5 && y <= 8 && Math.abs(dx) <= (y == 8 ? 1 : 2) && Math.abs(dz) <= (y == 8 ? 1 : 2))
                        return Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
                }
                if (x == 0 && z == -4 && y == 1) return Blocks.TORCH.defaultBlockState();
            }
            if (kind == 2) {
                int dx = x - 4, dz = z - 4;
                int radius = Math.max(Math.abs(dx), Math.abs(dz));
                if (y == 1 && radius <= 3) return Blocks.COBBLESTONE.defaultBlockState();
                if (y == 2 && radius == 2) return Blocks.COBBLESTONE.defaultBlockState();
                if (y == 2 && radius < 2) return Blocks.WATER.defaultBlockState();
                if ((y == 3 || y == 4) && Math.abs(dx) == 2 && Math.abs(dz) == 2) return Blocks.OAK_FENCE.defaultBlockState();
                if (y == 5 && radius <= 3) return Blocks.COBBLESTONE.defaultBlockState();
                if (dx == -3 && dz == -3 && y == 2) return Blocks.TORCH.defaultBlockState();
            }
            if (kind == 3) {
                if (Math.abs(x) >= 3 && Math.abs(x) <= width - 1 && z >= -depth + 2 && z <= -3) {
                    if (y == 4) return Blocks.OAK_PLANKS.defaultBlockState();
                    if (y <= 3 && (Math.abs(x) == 3 || z == -3 || Math.abs(x) == width - 1 || z == -depth + 2)) {
                        if (Math.abs(x) == 3 && z == -5 && y <= 2) return Blocks.AIR.defaultBlockState();
                        return Blocks.COBBLESTONE.defaultBlockState();
                    }
                    if (y == 1 && Math.abs(x) == 5 && (z == -depth + 4 || z == -depth + 3)) return bed(z == -depth + 3);
                    if (y == 1 && Math.abs(x) == width - 2 && z == -4) return Blocks.TORCH.defaultBlockState();
                }
            }
            if (kind == 5) {
                if ((z == -6 || z == 6) && Math.abs(x) <= 6) {
                    if (y == 5) return Blocks.OAK_PLANKS.defaultBlockState();
                    if (y < 5 && Math.abs(x) == 6) return Blocks.OAK_FENCE.defaultBlockState();
                }
                if (x == 4 && y == 1 && Math.abs(z) <= depth - 2) return Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH);
                if (x == -5 && z == -5 && y == 1) return Blocks.TORCH.defaultBlockState();
            }
            if (kind == 4 && x == -width + 1 && z == -depth + 1 && y == 1) return Blocks.TORCH.defaultBlockState();
            return null;
        }

        static BlockState house(int x, int z, int y) {
            if (Math.abs(x) > 4 || Math.abs(z) > 3 || y > 5) return null;
            if (y == 5) return Blocks.OAK_PLANKS.defaultBlockState();
            if (Math.abs(x) == 4 || Math.abs(z) == 3) {
                if (x == 0 && Math.abs(z) == 3 && y <= 2) return Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                if (y == 3 && z == 3 && Math.abs(x) == 2) return Blocks.GLASS.defaultBlockState();
                return (y <= 2 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).defaultBlockState();
            }
            if (y == 1 && x == -2 && (z == -1 || z == -2)) return bed(z == -2);
            if (y == 1 && x == 2 && z == -2) return Blocks.CRAFTING_TABLE.defaultBlockState();
            if (y == 1 && x == 2 && z == 1) return Blocks.TORCH.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        }

        static BlockState bed(boolean head) {
            return Blocks.WHITE_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.BED_PART, head ? BedPart.HEAD : BedPart.FOOT);
        }
    }
}

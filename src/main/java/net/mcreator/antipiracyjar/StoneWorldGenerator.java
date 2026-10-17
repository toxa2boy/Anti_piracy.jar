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
        final StoneWorldLayout.Sample sample;
        final int kind;
        Column(int wx, int wz, long seed) {
            sample = StoneWorldLayout.column(wx, wz, seed);
            kind = sample.kind;
        }
        BlockState at(int y) { return sample.at(y); }
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

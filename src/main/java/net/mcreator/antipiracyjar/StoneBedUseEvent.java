package net.mcreator.antipiracyjar;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber
public class StoneBedUseEvent {

    private static final int CHANCE_NOT_THIS_ONE = 60;
    private static final int CHANCE_SPAWN = 1;
    private static final int SESSION_TICKS = 40 * 20;
    private static final int SEARCH_RADIUS_CHUNKS = 80;
    private static final int SEARCH_OFFSET_BLOCKS = 1500;
    private static final int SCAN_RADIUS = 24;
    private static final int MAX_SEARCHES = 5;
    private static final boolean DEBUG = true;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SESSION_KEY = "StoneBedSession";
    
    private static final Set<BlockPos> VISITED_STRUCTURES = new HashSet<>();

    private static void dbg(String message, Object... args) {
        if (DEBUG) {
            LOGGER.info("[StoneBedUse] " + message, args);
        }
    }

    @SubscribeEvent
    public static void onBedClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(level.getChunkSource().getGenerator() instanceof StoneWorldGenerator)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return;

        if (player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        int roll = level.random.nextInt(100);
        ServerLevel overworld = player.getServer().overworld();

        if (roll < CHANCE_NOT_THIS_ONE) {
            dbg("event 1 (roll {})", roll);
            breakBed(level, pos);
            showNotThisOne(player);
        } else if (roll < CHANCE_NOT_THIS_ONE + CHANCE_SPAWN) {
            dbg("event 3 (roll {}): world spawn", roll);
            breakBed(level, pos);
            teleportToWorldSpawn(player, overworld);
        } else {
            dbg("event 2 (roll {}): structure for {} s", roll, SESSION_TICKS / 20);
            BlockPos spot = findUnvisitedStructureSpot(overworld);
            breakBed(level, pos);
            if (spot == null) {
                dbg("no safe or unvisited place found, falling back to the message");
                showNotThisOne(player);
            } else {
                startSession(player, overworld, spot);
            }
        }
    }

    private static void showNotThisOne(ServerPlayer player) {
        player.displayClientMessage(Component.literal("Not this one..."), true);
    }

    private static void breakBed(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            BlockPos other = part == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            if (level.getBlockState(other).getBlock() instanceof BedBlock) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void clearBedsFromInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof BedItem) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void startSession(ServerPlayer player, ServerLevel overworld, BlockPos spot) {
        CompoundTag session = new CompoundTag();
        session.putString("dim", player.level().dimension().location().toString());
        session.putDouble("x", player.getX());
        session.putDouble("y", player.getY());
        session.putDouble("z", player.getZ());
        session.putFloat("yaw", player.getYRot());
        session.putFloat("pitch", player.getXRot());
        session.putInt("ticks", SESSION_TICKS);
        player.getPersistentData().put(SESSION_KEY, session);

        dbg("session started, teleporting to {} {} {}", spot.getX(), spot.getY(), spot.getZ());
        teleport(player, overworld, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    private static void returnPlayer(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SESSION_KEY)) return;
        CompoundTag session = data.getCompound(SESSION_KEY);
        data.remove(SESSION_KEY);

        clearBedsFromInventory(player);

        MinecraftServer server = player.getServer();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(session.getString("dim")));
        ServerLevel back = server.getLevel(key);
        if (back == null) {
            back = server.overworld();
        }
        dbg("returning the player to {} {} {} {}", key.location(), session.getDouble("x"), session.getDouble("y"), session.getDouble("z"));
        teleport(player, back, session.getDouble("x"), session.getDouble("y"), session.getDouble("z"),
                session.getFloat("yaw"), session.getFloat("pitch"));
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        teleport(player, level, x, y, z, player.getYRot(), player.getXRot());
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.resetFallDistance();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        if (player.level() instanceof ServerLevel serverLevel && serverLevel.getChunkSource().getGenerator() instanceof StoneWorldGenerator) {
            clearBedsFromInventory(player);
        }

        CompoundTag data = player.getPersistentData();
        if (!data.contains(SESSION_KEY)) return;

        CompoundTag session = data.getCompound(SESSION_KEY);
        int left = session.getInt("ticks") - 1;
        if (left <= 0) {
            returnPlayer(player);
            return;
        }
        session.putInt("ticks", left);
        data.put(SESSION_KEY, session);
    }

    @SubscribeEvent
    public static void onPlayerDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().contains(SESSION_KEY)) return;

        event.setCanceled(true);
        dbg("damage during the session -> instant return");
        returnPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel && serverLevel.getChunkSource().getGenerator() instanceof StoneWorldGenerator) {
                clearBedsFromInventory(player);
            }
            returnPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove(SESSION_KEY);
        }
    }

    private static void teleportToWorldSpawn(ServerPlayer player, ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);

        int h = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos exact = new BlockPos(spawn.getX(), h, spawn.getZ());
        BlockPos spot = isSafeStand(overworld, exact) ? exact : surfaceSpotNear(overworld, spawn, 24);
        if (spot == null) {
            spot = exact;
        }
        clearBedsFromInventory(player);
        teleport(player, overworld, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    private static BlockPos findUnvisitedStructureSpot(ServerLevel overworld) {
        try {
            Registry<Structure> registry = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);

            List<Holder<Structure>> modStructures = registry.holders()
                    .filter(h -> h.key().location().getNamespace().equals("anti_piracyjar")
                            && h.key().location().getPath().startsWith("beta_"))
                    .<Holder<Structure>>map(h -> h)
                    .toList();

            List<Holder<Structure>> vanillaStructures = new ArrayList<>();
            for (ResourceKey<Structure> k : List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT,
                    BuiltinStructures.VILLAGE_SAVANNA, BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA,
                    BuiltinStructures.PILLAGER_OUTPOST, BuiltinStructures.DESERT_PYRAMID,
                    BuiltinStructures.JUNGLE_TEMPLE, BuiltinStructures.SWAMP_HUT, BuiltinStructures.IGLOO)) {
                registry.getHolder(k).ifPresent(vanillaStructures::add);
            }

            List<Holder<Structure>> combinedPool = new ArrayList<>();
            for (Holder<Structure> modStruct : modStructures) {
                combinedPool.add(modStruct);
                combinedPool.add(modStruct);
            }
            combinedPool.addAll(vanillaStructures);

            if (combinedPool.isEmpty()) return null;

            List<HolderSet<Structure>> attempts = new ArrayList<>();
            attempts.add(HolderSet.direct(combinedPool));

            BlockPos spawn = overworld.getSharedSpawnPos();
            int searches = 0;
            for (HolderSet<Structure> set : attempts) {
                if (searches++ >= MAX_SEARCHES) break;

                int ox = overworld.random.nextInt(SEARCH_OFFSET_BLOCKS * 2 + 1) - SEARCH_OFFSET_BLOCKS;
                int oz = overworld.random.nextInt(SEARCH_OFFSET_BLOCKS * 2 + 1) - SEARCH_OFFSET_BLOCKS;
                BlockPos center = new BlockPos(spawn.getX() + ox, 64, spawn.getZ() + oz);

                Pair<BlockPos, Holder<Structure>> found = overworld.getChunkSource().getGenerator()
                        .findNearestMapStructure(overworld, set, center, SEARCH_RADIUS_CHUNKS, false);
                if (found == null) {
                    continue;
                }

                BlockPos structureCenter = found.getFirst();
                if (VISITED_STRUCTURES.contains(structureCenter)) {
                    continue;
                }

                BlockPos spot = spotInStructure(overworld, structureCenter, found.getSecond().value());
                if (spot != null) {
                    VISITED_STRUCTURES.add(structureCenter);
                    return spot;
                }
            }
        } catch (Exception e) {
            LOGGER.error("[StoneBedUse] structure search failed", e);
        }
        return null;
    }

    private static BlockPos spotInStructure(ServerLevel level, BlockPos hint, Structure structure) {
        ChunkPos chunk = new ChunkPos(hint);
        List<StructureStart> starts = level.structureManager().startsForStructure(chunk, s -> s == structure);
        for (StructureStart start : starts) {
            if (!start.isValid()) continue;
            BlockPos spot = pickSpot(level, start);
            if (spot != null) return spot;
        }
        return surfaceSpotNear(level, hint, SCAN_RADIUS);
    }

    private static BlockPos pickSpot(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        int minX = Math.max(box.minX(), cx - SCAN_RADIUS), maxX = Math.min(box.maxX(), cx + SCAN_RADIUS);
        int minZ = Math.max(box.minZ(), cz - SCAN_RADIUS), maxZ = Math.min(box.maxZ(), cz + SCAN_RADIUS);
        int minY = Math.max(box.minY(), level.getMinBuildHeight() + 2);
        int maxY = Math.min(box.maxY() + 1, level.getMaxBuildHeight() - 4);

        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                level.getChunk(x, z);
            }
        }

        List<BlockPos> best = new ArrayList<>();
        int bestScore = -1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!isSafeStand(level, feet)) continue;

                    int score = 0;
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece.getBoundingBox().isInside(feet)) {
                            score += 4;
                            break;
                        }
                    }
                    if (!level.canSeeSky(feet)) score += 3;
                    BlockState floor = level.getBlockState(feet.below());
                    if (!floor.is(BlockTags.DIRT) && !floor.is(BlockTags.BASE_STONE_OVERWORLD) && !floor.is(BlockTags.SAND)
                            && !floor.is(Blocks.GRAVEL) && !floor.is(Blocks.SNOW_BLOCK)) {
                        score += 2;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        best.clear();
                    }
                    if (score == bestScore && best.size() < 64) {
                        best.add(feet);
                    }
                }
            }
        }
        if (best.isEmpty()) return null;
        BlockPos pick = best.get(level.random.nextInt(best.size()));
        return pick;
    }

    private static BlockPos surfaceSpotNear(ServerLevel level, BlockPos center, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = center.getX() + dx, z = center.getZ() + dz;
                    level.getChunk(x >> 4, z >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (isSafeStand(level, feet)) return feet;
                }
            }
        }
        return null;
    }

    private static boolean isSafeStand(ServerLevel level, BlockPos feet) {
        int y = feet.getY();
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 3) return false;

        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, floorPos, Direction.UP)) return false;
        if (floor.is(BlockTags.LEAVES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.FIRE)) return false;

        for (int i = 0; i < 2; i++) {
            BlockPos p = feet.above(i);
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
            if (s.is(BlockTags.FIRE) || s.is(Blocks.COBWEB) || s.is(Blocks.SWEET_BERRY_BUSH)
                    || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.CACTUS)) return false;
        }

        AABB body = new AABB(feet.getX() + 0.2, y, feet.getZ() + 0.2, feet.getX() + 0.8, y + 1.8, feet.getZ() + 0.8);
        if (!level.noCollision(body)) return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState s = level.getBlockState(feet.offset(dx, dy, dz));
                    if (s.getFluidState().is(FluidTags.LAVA) || s.is(BlockTags.FIRE)) return false;
                }
            }
        }
        return true;
    }
}
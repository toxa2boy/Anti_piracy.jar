package net.mcreator.antipiracyjar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class RoommateEvent {
    private record Change(BlockPos source, List<BlockPos> positions, List<BlockState> states) {}
    private static final ArrayDeque<Search> SEARCHES = new ArrayDeque<>();
    private static final Set<UUID> QUEUED = new HashSet<>();
    private static class Search {
        final UUID player;
        final List<BlockPos> centers = new ArrayList<>();
        final List<Change> choices = new ArrayList<>();
        final boolean test;
        int center;
        int cursor;
        Search(ServerPlayer player, boolean test) {
            this.player = player.getUUID();
            this.test = test;
            BlockPos respawn = player.getRespawnPosition();
            if (respawn != null && player.getRespawnDimension() == Level.OVERWORLD
                    && respawn.distSqr(player.blockPosition()) <= 96 * 96)
                centers.add(respawn);
            centers.add(player.blockPosition());
        }
    }

    private static String key(BlockPos pos) { return "roommateUsed_" + pos.asLong(); }

    private static boolean used(ServerLevel level, BlockPos pos) {
        return HorrorWorldState.get(level.getServer()).data.getBoolean(key(pos));
    }

    private static boolean empty(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.isInWorldBounds(pos) && level.getBlockState(pos).isAir()
                && level.getEntities(null, new AABB(pos)).isEmpty();
    }

    private static boolean supported(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos.below()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean observed(ServerLevel level, BlockPos pos) {
        Vec3 point = Vec3.atCenterOf(pos);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(point) > 64 * 64) continue;
            Vec3 delta = point.subtract(player.getEyePosition());
            if (delta.lengthSqr() < 9) return true;
            if (player.getLookAngle().dot(delta.normalize()) < .15) continue;
            if (level.clip(new ClipContext(player.getEyePosition(), point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static Change candidate(ServerLevel level, BlockPos source, boolean test) {
        if (!level.hasChunkAt(source) || used(level, source)) return null;
        BlockState state = level.getBlockState(source);
        boolean bed = state.getBlock() instanceof BedBlock;
        boolean chest = state.getBlock() instanceof ChestBlock;
        boolean door = state.getBlock() instanceof DoorBlock;
        if (!bed && !chest && !door) return null;
        if (bed && (state.getValue(BedBlock.PART) != BedPart.FOOT || state.getValue(BedBlock.OCCUPIED))) return null;
        if (chest && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) return null;
        if (door && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return null;
        Direction facing = bed ? state.getValue(BedBlock.FACING) : chest ? state.getValue(ChestBlock.FACING) : state.getValue(DoorBlock.FACING);
        Direction[] sides = chest ? new Direction[]{facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise()}
                : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()};
        for (Direction side : sides) {
            BlockPos pos = source.relative(side);
            if (used(level, pos) || !empty(level, pos) || !supported(level, pos) || (!test && observed(level, pos))) continue;
            if (bed) {
                BlockPos originalHead = source.relative(facing);
                BlockState head = level.getBlockState(originalHead);
                if (head.getBlock() != state.getBlock() || head.getValue(BedBlock.PART) != BedPart.HEAD) continue;
                BlockPos second = pos.relative(facing);
                if (!empty(level, second) || !supported(level, second) || used(level, second)
                        || (!test && observed(level, second))) continue;
                return new Change(source, List.of(pos, second), List.of(state.setValue(BedBlock.OCCUPIED, false),
                        state.setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.OCCUPIED, false)));
            }
            if (door) {
                BlockState top = level.getBlockState(source.above());
                if (top.getBlock() != state.getBlock() || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) continue;
                if (!empty(level, pos.above()) || used(level, pos.above()) || (!test && observed(level, pos.above()))) continue;
                BlockState bottom = state.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false)
                        .setValue(DoorBlock.HINGE, state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
                return new Change(source, List.of(pos, pos.above()), List.of(bottom, bottom.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
            }
            if (!empty(level, pos.above())) continue;
            return new Change(source, List.of(pos), List.of(state.setValue(ChestBlock.TYPE, ChestType.SINGLE).setValue(ChestBlock.WATERLOGGED, false)));
        }
        return null;
    }

    private static boolean apply(ServerLevel level, Change change) {
        for (BlockPos pos : change.positions()) if (!empty(level, pos)) return false;
        for (int i = 0; i < change.positions().size(); i++)
            level.setBlock(change.positions().get(i), change.states().get(i), 2);
        for (int i = 0; i < change.positions().size(); i++)
            level.updateNeighborsAt(change.positions().get(i), change.states().get(i).getBlock());
        var saved = HorrorWorldState.get(level.getServer());
        saved.data.putBoolean(key(change.source()), true);
        for (BlockPos pos : change.positions()) saved.data.putBoolean(key(pos), true);
        saved.setDirty();
        return true;
    }

    private static boolean enqueue(ServerPlayer player, boolean test) {
        if (!QUEUED.contains(player.getUUID()) && HorrorDirector.start(player,"roommate",300,test)) {
            QUEUED.add(player.getUUID()); SEARCHES.addLast(new Search(player,test));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 == 0) for (var player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD
                    || QUEUED.contains(player.getUUID())) continue;
            if (HorrorDirector.roll(player,"roommate",19,720,2400)
                    && !WatchEncounterEvent.busy(player.getUUID()) && !DontTurnAroundEvent.busy(player.getUUID())) enqueue(player, false);
        }
        Search search = SEARCHES.peekFirst();
        if (search == null) return;
        var player = server.getPlayerList().getPlayer(search.player);
        if (player == null || !player.isAlive() || player.level().dimension() != Level.OVERWORLD) { finish(search); return; }
        var level = server.overworld();
        int width = 33;
        int count = width * width * 13;
        for (int budget = 0; budget < 384 && search.center < search.centers.size(); budget++) {
            int index = search.cursor++;
            BlockPos pos = search.centers.get(search.center).offset(index % width - 16,
                    index / (width * width) - 6, (index / width) % width - 16);
            Change change = candidate(level, pos, search.test);
            if (change != null && search.choices.size() < 64) search.choices.add(change);
            if (search.cursor == count) {
                if (!search.choices.isEmpty()) break;
                search.center++;
                search.cursor = 0;
            }
        }
        if (search.center < search.centers.size() && search.cursor < count) return;
        boolean placed = false;
        while (!search.choices.isEmpty()) {
            Change old = search.choices.remove(level.random.nextInt(search.choices.size()));
            Change fresh = candidate(level, old.source(), search.test);
            if (fresh != null && apply(level, fresh)) { placed = true; break; }
        }
        HorrorDirector.finish(player,"roommate",placed);
        if (search.test) player.sendSystemMessage(Component.literal(placed ? "An extra piece of furniture appeared nearby."
                : "No suitable bed, single chest or door with free space was found within 16 blocks."));
        finish(search);
    }

    private static void finish(Search search) { SEARCHES.remove(search); QUEUED.remove(search.player); }

    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("roommatetest")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (player.level().dimension() != Level.OVERWORLD) return 0;
                    if (!enqueue(player,true)) {
                        context.getSource().sendFailure(Component.literal("Another horror scene is active.")); return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Searching nearby furniture..."), false);
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SEARCHES.clear(); QUEUED.clear(); }
}

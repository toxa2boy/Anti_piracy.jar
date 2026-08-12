package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber
public class FlatLandTreeScript {

    private static final Set<UUID> playersSpawnedTree = new HashSet<>();
    private static final Set<BlockPos> remainingTreeLogs = new HashSet<>();
    private static BlockPos currentTreePos = null;
    private static Direction treeDirection = null;
    private static boolean treeChopped = false;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        String dimPath = level.dimension().location().getPath();
        if (dimPath.equals("flat_land")) {
            UUID uuid = player.getUUID();

            if (!playersSpawnedTree.contains(uuid) && player.tickCount % 20 == 0) {
                Direction facing = player.getDirection();
                BlockPos targetPos = player.blockPosition().relative(facing, 30);

                if (level.isLoaded(targetPos)) {
                    BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, targetPos);

                    if (groundPos.getY() >= level.getMinBuildHeight()) {
                        buildManualOakTree(level, groundPos);

                        playersSpawnedTree.add(uuid);
                        currentTreePos = groundPos;
                        treeDirection = facing;
                        treeChopped = false;
                    }
                }
            }
        }
    }

    private static void buildManualOakTree(ServerLevel level, BlockPos pos) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);

        remainingTreeLogs.clear();

        for (int y = 2; y <= 5; y++) {
            int r = (y < 4) ? 2 : 1;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean isCorner = Math.abs(x) == r && Math.abs(z) == r;
                    if (isCorner && y != 4) continue;

                    BlockPos leafPos = pos.offset(x, y, z);
                    if (level.isEmptyBlock(leafPos)) {
                        level.setBlock(leafPos, leaves, 3);
                    }
                }
            }
        }

        for (int i = 0; i < 5; i++) {
            BlockPos logPos = pos.above(i);
            level.setBlock(logPos, log, 3);
            remainingTreeLogs.add(logPos.immutable());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (currentTreePos == null || treeChopped || remainingTreeLogs.isEmpty()) return;

        if (event.getLevel() instanceof ServerLevel level) {
            String dimPath = level.dimension().location().getPath();
            if (!dimPath.equals("flat_land")) return;

            BlockPos brokenPos = event.getPos().immutable();

            if (remainingTreeLogs.contains(brokenPos)) {
                remainingTreeLogs.remove(brokenPos);

                if (remainingTreeLogs.isEmpty()) {
                    treeChopped = true;

                    Direction facing = treeDirection != null ? treeDirection : event.getPlayer().getDirection();
                    BlockPos signTargetPos = currentTreePos.relative(facing, 20);
                    BlockPos signGroundPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, signTargetPos);

                    if (signGroundPos.getY() < level.getMinBuildHeight()) {
                        signGroundPos = currentTreePos.relative(facing, 20);
                    }

                    placeSign(level, signGroundPos, facing);
                }
            }
        }
    }

    private static void placeSign(ServerLevel level, BlockPos pos, Direction facing) {
        int rotation = Mth.floor(facing.getOpposite().toYRot() * 16.0F / 360.0F + 0.5D) & 15;
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rotation), 3);

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SignBlockEntity sign) {
            sign.updateText(text -> text
                    .setMessage(0, Component.literal("Build."))
                    .setMessage(1, Component.literal(""))
                    .setMessage(2, Component.literal(""))
                    .setMessage(3, Component.literal("")), true);
        }
    }

    @EventBusSubscriber(value = Dist.CLIENT)
    public static class FogHandler {
        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().getPath().equals("flat_land")) {
                event.setNearPlaneDistance(15.0F);
                event.setFarPlaneDistance(40.0F);
                event.setCanceled(true);
            }
        }
    }
}
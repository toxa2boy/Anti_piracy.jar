package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber
public class FlatLandTreeScript {

    private static final Set<UUID> playersSpawnedTree = new HashSet<>();
    private static final Set<BlockPos> remainingTreeLogs = new HashSet<>();
    private static BlockPos currentTreePos = null;
    private static Direction treeDirection = null;
    private static boolean treeChopped = false;

    private static BlockPos signPos = null;
    private static Direction signFacing = null;
    private static int blocksPlacedCount = 0;
    private static boolean structureSpawned = false;

    private static BlockPos houseMinPos = null;
    private static BlockPos houseMaxPos = null;
    private static final List<BlockPos> houseDoors = new ArrayList<>();

    private static boolean structureMusicStarted = false;
    private static int insideTicksCounter = 0;     
    private static boolean scriptActivated = false; 
    private static int scriptTimer = 0;             
    private static boolean doorReplaced = false;
    private static boolean secondPhaseTriggered = false;
    private static boolean finalSoundStopped = false;

    
    private static final int WHITE_SKY_TICK = 650;   
    private static final int FINAL_SIGN_TICK = 650;  
    private static final String FINAL_SIGN_KEY = "antipiracyjar_final_sign";
    
    private static volatile long whiteoutStartMillis = 0L;

    
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity().getPersistentData().getBoolean("antipiracyjar_tree_spawned")) {
            playersSpawnedTree.add(id);
        } else {
            playersSpawnedTree.remove(id);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;

        String dimPath = level.dimension().location().getPath();
        if (!dimPath.equals("flat_land")) return;

        UUID uuid = player.getUUID();

        
        
        if (!playersSpawnedTree.contains(uuid) && player.tickCount % 20 == 0) {
            Direction facing = player.getDirection();
            BlockPos targetPos = player.blockPosition().relative(facing, 30);

            level.getChunkSource().getChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4, true);
            BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, targetPos);

            if (groundPos.getY() >= level.getMinBuildHeight()) {
                buildManualOakTree(level, groundPos);

                playersSpawnedTree.add(uuid);
                
                player.getPersistentData().putBoolean("antipiracyjar_tree_spawned", true);
                currentTreePos = groundPos;
                treeDirection = facing;
                treeChopped = false;
            }
        }

        if (structureSpawned && houseMinPos != null && houseMaxPos != null) {
            BlockPos pPos = player.blockPosition();

            boolean isInsideStructureArea = pPos.getX() >= houseMinPos.getX() && pPos.getX() <= houseMaxPos.getX() &&
                                            pPos.getY() >= houseMinPos.getY() && pPos.getY() <= houseMaxPos.getY() + 5 &&
                                            pPos.getZ() >= houseMinPos.getZ() && pPos.getZ() <= houseMaxPos.getZ();

            if (isInsideStructureArea && !structureMusicStarted) {
                structureMusicStarted = true;
                
                level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack().withSuppressedOutput(),
                        "stopsound @a"
                );

                SoundEvent distortedMusic = SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "music_disc.11")
                );
                level.playSound(null, player.blockPosition(), distortedMusic, SoundSource.MUSIC, 1.0F, 0.5F);
            }

            if (!houseDoors.isEmpty()) {
                BlockPos primaryDoor = houseDoors.get(0);

                
                boolean isInsideRoom = pPos.getX() >= houseMinPos.getX() + 8 && pPos.getX() <= houseMinPos.getX() + 13 &&
                                       pPos.getY() >= houseMinPos.getY()     && pPos.getY() <= houseMinPos.getY() + 4  &&
                                       pPos.getZ() >= houseMinPos.getZ() + 8 && pPos.getZ() <= houseMinPos.getZ() + 13 &&
                                       pPos.getZ() >= (primaryDoor.getZ() + 1);

                if (!scriptActivated) {
                    if (isInsideRoom) {
                        insideTicksCounter++;

                        if (insideTicksCounter > 40) { 
                            scriptActivated = true; 
                            scriptTimer = 0;
                        }
                    } else {
                        insideTicksCounter = 0;
                    }
                }

                if (scriptActivated) {
                    scriptTimer++;

                    if (scriptTimer == 1) {
                        if (!doorReplaced) {
                            replaceDoorsAndPlaceSingleSign(level);
                            doorReplaced = true;
                        }
                    }

                    if (scriptTimer >= 1 && scriptTimer <= 200) {
                        Direction pFacing = player.getDirection();

                        if (scriptTimer == 1) {
                            playKnockSound(level, player.blockPosition().relative(pFacing, 1), 1.0F, 0.9F);
                        } else if (scriptTimer == 25) {
                            playKnockSound(level, player.blockPosition().relative(pFacing.getCounterClockWise(), 5), 0.35F, 1.1F);
                        } else if (scriptTimer == 50) {
                            playKnockSound(level, player.blockPosition().relative(pFacing.getOpposite(), 1), 0.9F, 0.8F);
                        } else if (scriptTimer == 80) {
                            playKnockSound(level, player.blockPosition().relative(pFacing.getClockWise(), 4), 0.5F, 1.0F);
                        } else if (scriptTimer == 110) {
                            playKnockSound(level, player.blockPosition().relative(pFacing.getCounterClockWise(), 2), 0.8F, 1.2F);
                        } else if (scriptTimer == 140) {
                            playKnockSound(level, player.blockPosition().relative(pFacing, 7), 0.25F, 0.7F);
                        } else if (scriptTimer == 170) {
                            playKnockSound(level, player.blockPosition().relative(pFacing.getOpposite(), 3).relative(pFacing.getClockWise(), 2), 0.6F, 0.95F);
                        } else if (scriptTimer == 200) {
                            playKnockSound(level, player.blockPosition().relative(pFacing, 2), 0.4F, 0.6F);
                        }
                    }

                    if (scriptTimer == 260 && !secondPhaseTriggered) {
                        triggerBedAndSignsPhase(level);
                        secondPhaseTriggered = true;
                    }

                    if (scriptTimer == 360) {
                        BlockPos soundPos = houseDoors.isEmpty() ? player.blockPosition() : houseDoors.get(0);
                        level.playSound(null, soundPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    if (scriptTimer == 390) {
                        BlockPos soundPos = houseDoors.isEmpty() ? player.blockPosition() : houseDoors.get(0);
                        level.playSound(null, soundPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    if (scriptTimer == 410) {
                        for (BlockPos pos : houseDoors) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                        
                        level.setBlock(houseMinPos.offset(13, 4, 8), Blocks.AIR.defaultBlockState(), 3);
                        
                        level.setBlock(houseMinPos.offset(11, 1, 9), Blocks.AIR.defaultBlockState(), 3);

                        BlockPos finalSignPos = houseMinPos.offset(12, 1, -7);
                        level.setBlock(finalSignPos, Blocks.OAK_SIGN.defaultBlockState(), 3);
                        BlockEntity finalSignBe = level.getBlockEntity(finalSignPos);
                        if (finalSignBe instanceof SignBlockEntity finalSign) {
                            finalSign.updateText(text -> text
                                    .setMessage(0, Component.literal("Why would you"))
                                    .setMessage(1, Component.literal("leave?"))
                                    .setMessage(2, Component.literal("It's beautiful"))
                                    .setMessage(3, Component.literal("here.")), true);
                        }
                    }

                    
                    
                    if (scriptTimer == 550) {
                        BlockPos videoSignPos = houseMinPos.offset(6, 1, 1);
                        
                        level.setBlock(videoSignPos, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 8), 3);
                        BlockEntity videoSignBe = level.getBlockEntity(videoSignPos);
                        if (videoSignBe instanceof SignBlockEntity videoSign) {
                            videoSign.updateText(text -> text
                                    .setMessage(0, Component.literal("There is no"))
                                    .setMessage(1, Component.literal("real world."))
                                    .setMessage(2, Component.literal("Not for you."))
                                    .setMessage(3, Component.literal("Not anymore.")), true);
                        }
                    }

                    
                    if (scriptTimer == WHITE_SKY_TICK) {
                        whiteoutStartMillis = System.currentTimeMillis();
                    }

                    
                    if (scriptTimer == FINAL_SIGN_TICK) {
                        BlockPos digSignPos = houseMinPos.offset(4, 1, -14);
                        
                        level.setBlock(digSignPos, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0), 3);
                        BlockEntity digSignBe = level.getBlockEntity(digSignPos);
                        if (digSignBe instanceof SignBlockEntity digSign) {
                            digSign.updateText(text -> text
                                    .setMessage(0, Component.literal("Dig."))
                                    .setMessage(1, Component.literal(";)"))
                                    .setMessage(2, Component.literal(""))
                                    .setMessage(3, Component.literal("")), true);
                        }
                        player.getPersistentData().putBoolean(FINAL_SIGN_KEY, true);
                    }

                    if (scriptTimer >= 410 && !isInsideRoom && !finalSoundStopped) {
                        level.getServer().getCommands().performPrefixedCommand(
                                level.getServer().createCommandSourceStack().withSuppressedOutput(),
                                "stopsound @a"
                        );
                        finalSoundStopped = true;
                    }
                }
            }
        }
    }

    private static void triggerBedAndSignsPhase(ServerLevel level) {
        if (houseMinPos != null && houseMaxPos != null) {
            List<BlockPos> bedsToRemove = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(houseMinPos, houseMaxPos)) {
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.BEDS) || state.getBlock().getName().getString().toLowerCase().contains("bed")) {
                    bedsToRemove.add(pos.immutable());
                }
            }
            for (BlockPos bedPos : bedsToRemove) {
                level.removeBlockEntity(bedPos);
                level.setBlock(bedPos, Blocks.AIR.defaultBlockState(), 18); 
            }
        }

        if (houseMinPos == null) return;

        BlockPos plank1Pos = houseMinPos.offset(10, 2, 12);
        BlockPos sign1Pos = houseMinPos.offset(10, 2, 11); 

        level.setBlock(plank1Pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(sign1Pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);

        BlockEntity be1 = level.getBlockEntity(sign1Pos);
        if (be1 instanceof SignBlockEntity sign1) {
            sign1.updateText(text -> text
                    .setMessage(0, Component.literal("Don't you think"))
                    .setMessage(1, Component.literal("it would be a"))
                    .setMessage(2, Component.literal("nice place to"))
                    .setMessage(3, Component.literal("die?")), true);
        }

        BlockPos sign2Pos = houseMinPos.offset(12, 2, 12); 
        
        level.setBlock(sign2Pos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 3);

        BlockEntity be2 = level.getBlockEntity(sign2Pos);
        if (be2 instanceof SignBlockEntity sign2) {
            sign2.updateText(text -> text
                    .setMessage(0, Component.literal("You won't"))
                    .setMessage(1, Component.literal("need to sleep"))
                    .setMessage(2, Component.literal("anymore"))
                    .setMessage(3, Component.literal("")), true);
        }
    }

    private static void playKnockSound(ServerLevel level, BlockPos pos, float volume, float pitch) {
        SoundEvent knockSound = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "stuk-v-dver")
        );
        level.playSound(null, pos, knockSound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void replaceDoorsAndPlaceSingleSign(ServerLevel level) {
        if (houseDoors.isEmpty()) return;

        BlockPos primaryDoor = null;
        for (BlockPos pos : houseDoors) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                primaryDoor = pos;
                break;
            }
        }
        if (primaryDoor == null) primaryDoor = houseDoors.get(0);

        for (BlockPos pos : houseDoors) {
            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }

        BlockPos exactSignPos = primaryDoor.south(); 

        level.setBlock(exactSignPos, Blocks.OAK_SIGN.defaultBlockState(), 3);
        BlockEntity blockEntity = level.getBlockEntity(exactSignPos);
        if (blockEntity instanceof SignBlockEntity sign) {
            sign.updateText(text -> text
                    .setMessage(0, Component.literal("I died here"))
                    .setMessage(1, Component.literal(""))
                    .setMessage(2, Component.literal(""))
                    .setMessage(3, Component.literal("")), true);
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
        if (event.getLevel() instanceof ServerLevel level) {
            String dimPath = level.dimension().location().getPath();
            if (!dimPath.equals("flat_land")) return;

            BlockPos brokenPos = event.getPos().immutable();

            if (scriptActivated && houseMinPos != null && houseMaxPos != null) {
                if (brokenPos.getX() >= houseMinPos.getX() && brokenPos.getX() <= houseMaxPos.getX()
                        && brokenPos.getY() >= houseMinPos.getY() && brokenPos.getY() <= houseMaxPos.getY()
                        && brokenPos.getZ() >= houseMinPos.getZ() && brokenPos.getZ() <= houseMaxPos.getZ()) {
                    event.setCanceled(true);
                    return;
                }
            }

            if (currentTreePos != null && !treeChopped && !remainingTreeLogs.isEmpty()) {
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

                        signPos = signGroundPos;
                        signFacing = facing;
                        blocksPlacedCount = 0;
                        structureSpawned = false;
                        structureMusicStarted = false;
                        insideTicksCounter = 0;
                        scriptActivated = false;
                        scriptTimer = 0;
                        doorReplaced = false;
                        secondPhaseTriggered = false;
                        finalSoundStopped = false;
                        whiteoutStartMillis = 0L;
                        event.getPlayer().getPersistentData().remove(FINAL_SIGN_KEY);
                        houseDoors.clear();
                    }
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

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (signPos == null || structureSpawned) return;

        if (event.getEntity() instanceof Player player && event.getLevel() instanceof ServerLevel level) {
            String dimPath = level.dimension().location().getPath();
            if (!dimPath.equals("flat_land")) return;

            if (event.getPos().distSqr(signPos) < 2500) {
                blocksPlacedCount++;

                if (blocksPlacedCount >= 15) {
                    spawnFlatLandHome(level);
                    structureSpawned = true;
                }
            }
        }
    }

    private static void spawnFlatLandHome(ServerLevel level) {
        if (signPos == null || signFacing == null) return;

        Direction right = signFacing.getClockWise();
        BlockPos targetPos = signPos.relative(right, 45);

        int chunkX = targetPos.getX() >> 4;
        int chunkZ = targetPos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, true);
            }
        }

        BlockPos spawnPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, targetPos).below();

        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "flat_land_home");
        Optional<StructureTemplate> template = level.getServer().getStructureManager().get(structureId);

        template.ifPresent(t -> {
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(Rotation.NONE)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(false);

            t.placeInWorld(level, spawnPos, spawnPos, settings, level.random, 3);

            Vec3i size = t.getSize();
            houseMinPos = spawnPos;
            houseMaxPos = spawnPos.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

            houseDoors.clear();
            for (BlockPos pos : BlockPos.betweenClosed(houseMinPos, houseMaxPos)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock || state.is(BlockTags.DOORS) || state.getBlock().getName().getString().toLowerCase().contains("door")) {
                    houseDoors.add(pos.immutable());
                }
            }
        });
    }

    @EventBusSubscriber(value = Dist.CLIENT)
    public static class FogHandler {
        
        private static float whiteProgress() {
            long start = whiteoutStartMillis;
            if (start <= 0L) return 0.0F;
            return Mth.clamp((System.currentTimeMillis() - start) / 2000.0F, 0.0F, 1.0F);
        }

        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().getPath().equals("flat_land")) {
                float near = 15.0F;
                float far = 40.0F;
                float p = whiteProgress();
                if (p > 0.0F && event.getMode() == FogRenderer.FogMode.FOG_SKY) {
                    
                    near = 15.0F * (1.0F - p);
                    far = 40.0F - 39.0F * p;
                }
                event.setNearPlaneDistance(near);
                event.setFarPlaneDistance(far);
                event.setCanceled(true);
            }
        }

        
        
        private static final float GLARE_MAX_ALPHA = 0.6F;

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.dimension().location().getPath().equals("flat_land")) return;
            float p = whiteProgress();
            if (p <= 0.0F) return;
            float pulse = 0.9F + 0.1F * (float) Math.sin(System.currentTimeMillis() / 400.0D);
            int alpha = Mth.clamp((int) (255.0F * GLARE_MAX_ALPHA * p * pulse), 0, 255);
            GuiGraphics g = event.getGuiGraphics();
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xFFFFFF);
        }

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.dimension().location().getPath().equals("flat_land")) {
                float p = whiteProgress();
                if (p > 0.0F) {
                    event.setRed(event.getRed() + (1.0F - event.getRed()) * p);
                    event.setGreen(event.getGreen() + (1.0F - event.getGreen()) * p);
                    event.setBlue(event.getBlue() + (1.0F - event.getBlue()) * p);
                }
            }
        }
    }
}
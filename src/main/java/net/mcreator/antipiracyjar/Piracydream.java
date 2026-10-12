package net.mcreator.antipiracyjar;

import io.netty.buffer.ByteBuf;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class Piracydream {

    public static volatile boolean clientDreamActive = false;

    public Piracydream() {
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        new Piracydream();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void clientLoad(FMLClientSetupEvent event) {
    }

    public record DreamStatePayload(boolean active) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DreamStatePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "dream_state"));

        public static final StreamCodec<ByteBuf, DreamStatePayload> STREAM_CODEC =
                ByteBufCodecs.BOOL.map(DreamStatePayload::new, DreamStatePayload::active);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(DreamStatePayload.TYPE, DreamStatePayload.STREAM_CODEC, (payload, context) -> {
            clientDreamActive = payload.active();
        });
    }

    @EventBusSubscriber
    private static class PiracydreamForgeBusEvents {

        private static final String DREAM_ACTIVE_KEY = "PiracyDreamActive";

        private static final ResourceKey<Level> FLAT_LAND_DIM = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "flat_land"));

        private static boolean isInFlatLand(ServerPlayer player) {
            return player.level().dimension().equals(FLAT_LAND_DIM);
        }

        private static void endDream(ServerPlayer player, boolean teleportBack) {
            CompoundTag data = player.getPersistentData();

            data.putBoolean(DREAM_ACTIVE_KEY, false);
            PacketDistributor.sendToPlayer(player, new DreamStatePayload(false));

            if (data.contains("StructMinX")) {
                int minX = data.getInt("StructMinX");
                int minY = data.getInt("StructMinY");
                int minZ = data.getInt("StructMinZ");
                int sizeX = data.getInt("StructSizeX");
                int sizeY = data.getInt("StructSizeY");
                int sizeZ = data.getInt("StructSizeZ");

                ServerLevel flatLand = player.getServer().getLevel(FLAT_LAND_DIM);
                if (flatLand != null) {
                    for (BlockPos p : BlockPos.betweenClosed(minX, minY, minZ, minX + sizeX - 1, minY + sizeY - 1, minZ + sizeZ - 1)) {
                        flatLand.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }

                data.remove("StructMinX");
                data.remove("StructMinY");
                data.remove("StructMinZ");
                data.remove("StructSizeX");
                data.remove("StructSizeY");
                data.remove("StructSizeZ");
            }

            String binaryName = "01010000 01101001 01110010 01100001 01100011 01111001 00100000 01101001 01110011 00100000 01100001 00100000 01100011 01110010 01101001 01101101 01100101";
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(Items.PAPER)) {
                    Component customName = stack.get(DataComponents.CUSTOM_NAME);
                    if (customName != null && customName.getString().equals("Wake up.")) {
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal(binaryName));
                    }
                }
            }

            String dimName = data.getString("PreDreamDim");
            if (teleportBack && !dimName.isEmpty()) {
                ResourceKey<Level> returnDimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimName));
                ServerLevel returnLevel = player.getServer().getLevel(returnDimKey);

                if (returnLevel != null) {
                    double px = data.getDouble("PreDreamX");
                    double py = data.getDouble("PreDreamY");
                    double pz = data.getDouble("PreDreamZ");

                    player.teleportTo(returnLevel, px, py, pz, player.getYRot(), player.getXRot());
                }
            }

            data.remove("PreDreamDim");
            data.remove("PreDreamX");
            data.remove("PreDreamY");
            data.remove("PreDreamZ");
        }

        @SubscribeEvent
        public static void serverLoad(ServerStartingEvent event) {
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {

                if (player.getPersistentData().getBoolean(DREAM_ACTIVE_KEY) && isInFlatLand(player)) {
                    PacketDistributor.sendToPlayer(player, new DreamStatePayload(true));
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {

                if (player.getPersistentData().getBoolean(DREAM_ACTIVE_KEY) && !isInFlatLand(player)) {
                    endDream(player, false);
                }

                AdvancementHolder sleepAdv = player.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("minecraft", "adventure/sleep_in_bed"));
                if (sleepAdv != null && !player.getAdvancements().getOrStartProgress(sleepAdv).isDone()) {
                    player.getPersistentData().putBoolean("PiracyDreamHad", false);
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                CompoundTag data = player.getPersistentData();

                if (data.getBoolean("PiracyDreamHad") || data.getBoolean(DREAM_ACTIVE_KEY)) {
                    return;
                }

                ServerLevel flatLand = player.getServer().getLevel(FLAT_LAND_DIM);
                if (flatLand == null) {
                    return;
                }

                data.putBoolean("PiracyDreamHad", true);
                data.putString("PreDreamDim", player.level().dimension().location().toString());
                data.putDouble("PreDreamX", player.getX());
                data.putDouble("PreDreamY", player.getY());
                data.putDouble("PreDreamZ", player.getZ());

                player.getSleepingPos().ifPresent(bedPos -> {
                    BlockState bedState = player.level().getBlockState(bedPos);
                    if (bedState.getBlock() instanceof BedBlock) {
                        player.level().setBlock(bedPos, bedState.setValue(BedBlock.OCCUPIED, false), 3);
                    }
                });
                player.stopSleeping();

                int originX = Math.abs(player.getUUID().hashCode() % 1000) * 200;
                int originZ = Math.abs((player.getUUID().hashCode() >> 16) % 1000) * 200;
                BlockPos structurePos = new BlockPos(originX, 240, originZ);

                StructureTemplateManager templateManager = flatLand.getStructureManager();
                Optional<StructureTemplate> template = templateManager.get(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "piracy_parkour"));
                if (template.isPresent()) {
                    StructureTemplate struct = template.get();
                    struct.placeInWorld(flatLand, structurePos, structurePos, new StructurePlaceSettings(), flatLand.random, 2);

                    Vec3i size = struct.getSize();
                    data.putInt("StructMinX", originX);
                    data.putInt("StructMinY", 240);
                    data.putInt("StructMinZ", originZ);
                    data.putInt("StructSizeX", size.getX());
                    data.putInt("StructSizeY", size.getY());
                    data.putInt("StructSizeZ", size.getZ());
                }

                double targetX = originX + 0.5;
                double targetY = -1;
                double targetZ = originZ + 0.5;

                for (int y = 255; y >= 200; y--) {
                    BlockPos checkPos = new BlockPos(originX, y, originZ);
                    if (!flatLand.isEmptyBlock(checkPos) && flatLand.getBlockState(checkPos).isSolid()) {
                        targetY = y + 1.0;
                        break;
                    }
                }

                if (targetY == -1) {
                    for (int dx = -2; dx <= 2 && targetY == -1; dx++) {
                        for (int dz = -2; dz <= 2 && targetY == -1; dz++) {
                            for (int y = 255; y >= 200; y--) {
                                BlockPos checkPos = new BlockPos(originX + dx, y, originZ + dz);
                                if (!flatLand.isEmptyBlock(checkPos) && flatLand.getBlockState(checkPos).isSolid()) {
                                    targetX = originX + dx + 0.5;
                                    targetZ = originZ + dz + 0.5;
                                    targetY = y + 1.0;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (targetY == -1) {
                    targetY = 250.0;
                    flatLand.setBlock(new BlockPos(originX, 249, originZ), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }

                player.teleportTo(flatLand, targetX, targetY, targetZ, player.getYRot(), player.getXRot());

                data.putBoolean(DREAM_ACTIVE_KEY, true);
                PacketDistributor.sendToPlayer(player, new DreamStatePayload(true));
            }
        }

        @SubscribeEvent
        public static void onPlayerDamage(LivingIncomingDamageEvent event) {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                CompoundTag data = player.getPersistentData();

                if (data.getBoolean(DREAM_ACTIVE_KEY) && isInFlatLand(player)) {
                    event.setCanceled(true);
                    player.setHealth(player.getMaxHealth());
                    endDream(player, true);
                }
            }
        }
    }

    @EventBusSubscriber(value = Dist.CLIENT)
    private static class PiracydreamClientEvents {

        private static final ResourceLocation DREAM_SOUND_ID =
                ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "pomehiogg");

        private static final float DREAM_VOLUME = 0.15F;

        private static final float DREAM_PITCH = 0.5F;

        private static DreamLoopSound loop = null;

        private static boolean isInDream() {
            return Minecraft.getInstance().level != null && clientDreamActive;
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.level == null) {
                clientDreamActive = false;
            }

            if (isInDream()) {

                mc.getMusicManager().stopPlaying();

                if (loop == null || loop.isStopped()) {
                    loop = new DreamLoopSound();
                    mc.getSoundManager().play(loop);
                }
            } else if (loop != null) {

                mc.getSoundManager().stop(loop);
                loop = null;
            }
        }

        @SubscribeEvent
        public static void onPlaySound(PlaySoundEvent event) {
            if (!isInDream()) {
                return;
            }
            SoundInstance sound = event.getSound();
            if (sound == null) {
                return;
            }

            if (sound.getLocation().equals(DREAM_SOUND_ID)) {
                return;
            }

            boolean playerStep = sound.getSource() == SoundSource.PLAYERS
                    && sound.getLocation().getPath().endsWith(".step");
            if (!playerStep) {
                event.setSound(null); 
            }
        }

        private static class DreamLoopSound extends AbstractTickableSoundInstance {
            DreamLoopSound() {
                super(SoundEvent.createVariableRangeEvent(DREAM_SOUND_ID), SoundSource.MASTER, RandomSource.create());
                this.looping = true;
                this.delay = 0;
                this.volume = DREAM_VOLUME;
                this.pitch = DREAM_PITCH;
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
            }

            @Override
            public void tick() {
                if (!isInDream()) {
                    this.stop();
                }
            }
        }
    }
}
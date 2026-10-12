package net.mcreator.antipiracyjar;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public class LoginHorrorEvent {
    private static final int FIRST_KICK_DELAY = 15 * 20;
    private static final int BLINDNESS_BEFORE_STRUCTURES = 10;
    private static final int KICK_AFTER_STRUCTURES = 5;
    private static final String CONNECTION_ERROR = "Connection error. Please try again.";
    private static final String ALREADY_ONLINE = "This player is already online.";
    private static final Map<UUID, Integer> LOGIN_STAGES = new HashMap<>();
    private static final List<PendingAction> PENDING_ACTIONS = new ArrayList<>();

    public record HorrorDisconnectPayload(String reason) implements CustomPacketPayload {
        public static final Type<HorrorDisconnectPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "horror_disconnect"));
        public static final StreamCodec<ByteBuf, HorrorDisconnectPayload> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(HorrorDisconnectPayload::new, HorrorDisconnectPayload::reason);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
    public static class NetworkEvents {
        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(
                    HorrorDisconnectPayload.TYPE,
                    HorrorDisconnectPayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(() -> {
                        context.connection().disconnect(Component.literal(payload.reason()));
                    })
            );
        }
    }

    private enum ActionType {
        BLIND_PLAYER,
        FIRST_KICK,
        SECOND_KICK,
        FALLBACK_KICK,
        START_SCENE
    }

    private static class PendingAction {
        private final ServerPlayer player;
        private ActionType type;
        private int ticks;
        private String reason;

        private PendingAction(ServerPlayer player, ActionType type, int ticks) {
            this.player = player;
            this.type = type;
            this.ticks = ticks;
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        PENDING_ACTIONS.removeIf(action -> action.player.getUUID().equals(playerId));
        int stage = LOGIN_STAGES.getOrDefault(playerId, 0);
        if (stage == 0) {
            PENDING_ACTIONS.add(new PendingAction(player, ActionType.BLIND_PLAYER, FIRST_KICK_DELAY));
        } else if (stage == 1) {
            player.removeEffect(MobEffects.BLINDNESS);
            PENDING_ACTIONS.add(new PendingAction(player, ActionType.SECOND_KICK, 20));
        } else if (stage == 2) {
            player.removeEffect(MobEffects.BLINDNESS);
            LOGIN_STAGES.put(playerId, 3);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_ACTIONS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<PendingAction> iterator = PENDING_ACTIONS.iterator();
        while (iterator.hasNext()) {
            PendingAction action = iterator.next();
            ServerPlayer player = action.player;
            if (event.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
                iterator.remove();
                continue;
            }
            if (--action.ticks > 0) {
                continue;
            }

            if (action.type == ActionType.BLIND_PLAYER) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, false));
                action.type = ActionType.START_SCENE;
                action.ticks = BLINDNESS_BEFORE_STRUCTURES;
            } else if (action.type == ActionType.START_SCENE) {
                startScene(player);
                LOGIN_STAGES.put(player.getUUID(), 1);
                action.type = ActionType.FIRST_KICK;
                action.ticks = KICK_AFTER_STRUCTURES;
            } else if (action.type == ActionType.FALLBACK_KICK) {
                player.connection.disconnect(Component.literal(action.reason));
                iterator.remove();
            } else {
                boolean first = action.type == ActionType.FIRST_KICK;
                action.reason = first ? CONNECTION_ERROR : ALREADY_ONLINE;
                LOGIN_STAGES.put(player.getUUID(), first ? 1 : 2);
                PacketDistributor.sendToPlayer(player, new HorrorDisconnectPayload(action.reason));
                action.type = ActionType.FALLBACK_KICK;
                action.ticks = 40;
            }
        }
    }

    private static void startScene(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();

        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2.0 * i / 3.0) + random.nextDouble() * 0.5;
            int distance = 8 + random.nextInt(5);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, player.getBlockY(), z));
            buildRuin(level, ground, random);
        }

        spawnOldItems(level, player.blockPosition(), random);

        SoundEvent ambient = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "ambient")
        );
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ambient, SoundSource.AMBIENT, 1.0F, 0.65F);
    }

    private static void buildRuin(ServerLevel level, BlockPos ground, RandomSource random) {
        BlockState[] ruinBlocks = {
                Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                Blocks.OAK_PLANKS.defaultBlockState()
        };

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (random.nextFloat() < 0.48F) {
                    continue;
                }
                BlockPos pos = ground.offset(dx, 0, dz);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, ruinBlocks[random.nextInt(ruinBlocks.length)], 3);
                }
            }
        }

        for (int pillar = 0; pillar < 4; pillar++) {
            int dx = (pillar % 2 == 0) ? -2 : 2;
            int dz = (pillar < 2) ? -2 : 2;
            int height = 1 + random.nextInt(3);
            for (int dy = 1; dy <= height; dy++) {
                BlockPos pos = ground.offset(dx, dy, dz);
                if (level.getBlockState(pos).canBeReplaced()) {
                    level.setBlock(pos, ruinBlocks[random.nextInt(ruinBlocks.length)], 3);
                }
            }
        }

        BlockPos cobwebPos = ground.offset(random.nextInt(3) - 1, 2, random.nextInt(3) - 1);
        if (level.getBlockState(cobwebPos).canBeReplaced()) {
            level.setBlock(cobwebPos, Blocks.COBWEB.defaultBlockState(), 3);
        }
    }

    private static void spawnOldItems(ServerLevel level, BlockPos center, RandomSource random) {
        ItemStack[] oldItems = {
                new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.WOODEN_SWORD),
                new ItemStack(Items.ROTTEN_FLESH),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.BONE)
        };

        oldItems[0].set(DataComponents.DAMAGE, oldItems[0].getMaxDamage() - 2);
        oldItems[1].set(DataComponents.DAMAGE, oldItems[1].getMaxDamage() - 1);
        oldItems[3].set(DataComponents.CUSTOM_NAME, Component.literal("Old player data"));

        for (ItemStack stack : oldItems) {
            double x = center.getX() + 0.5 + (random.nextDouble() - 0.5) * 5.0;
            double z = center.getZ() + 0.5 + (random.nextDouble() - 0.5) * 5.0;
            BlockPos floor = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, center.getY(), z));
            ItemEntity item = new ItemEntity(level, x, floor.getY() + 0.15, z, stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }
}

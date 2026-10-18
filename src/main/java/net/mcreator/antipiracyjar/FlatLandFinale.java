package net.mcreator.antipiracyjar;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class FlatLandFinale {
    private static final String ARMED = "antipiracyjar_final_sign";
    private static final String TICKS = "antipiracyjar_final_fall_ticks";
    private static final String RETURN = "antipiracyjar_final_return";
    public static final String DONE = "antipiracyjar_final_done";
    private static final String STONE_ENTERED = "antipiracyjar_stone_entered";
    private static final ResourceKey<Level> STONE_WORLD = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "kamniizmerenie"));

    private static boolean active(ServerPlayer player) {
        return player.level().dimension().location().equals(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "flat_land"))
                && (player.getPersistentData().getBoolean(ARMED) || player.getPersistentData().getBoolean(RETURN));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active(player)) return;
        var data = player.getPersistentData();
        player.fallDistance = 0;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        if (player.getY() < player.level().getMinBuildHeight() + 2) {
            player.teleportTo(player.getX(), player.level().getMinBuildHeight() + 8, player.getZ());
        }
        if (data.getBoolean(RETURN)) return;
        boolean falling = !player.onGround() && !player.isInWater() && !player.onClimbable()
                && !player.getAbilities().flying && player.getDeltaMovement().y < -0.01;
        int ticks = falling ? data.getInt(TICKS) + 1 : 0;
        data.putInt(TICKS, ticks);
        if (ticks >= 80) {
            data.putBoolean(RETURN, true);
            data.remove(ARMED);
            data.remove(TICKS);
            player.server.getPlayerList().saveAll();
            PacketDistributor.sendToPlayer(player, new VideoPayload(true));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        data.remove(TICKS);
        if (!data.getBoolean(RETURN) && !(data.getBoolean(DONE) && !data.getBoolean(STONE_ENTERED))) return;
        var level = player.server.getLevel(STONE_WORLD);
        if (level == null) {
            com.mojang.logging.LogUtils.getLogger().error("Stone world is missing; keeping pending finale transfer for {}", player.getUUID());
            return;
        }
        BlockPos target = new BlockPos(24, StoneWorldGenerator.FLOOR + 1, 24);
        level.getChunk(target);
        if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()
                || !level.getBlockState(target.below()).isSolidRender(level, target.below())) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlock(target.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 0; y <= 3; y++) level.setBlock(target.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 180, 0);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.removeEffect(MobEffects.SLOW_FALLING);
        data.remove(RETURN);
        data.remove(ARMED);
        data.remove("PiracyDreamActive");
        data.putBoolean(DONE, true);
        data.putBoolean(STONE_ENTERED, true);
        player.server.getPlayerList().saveAll();
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        var original = event.getOriginal().getPersistentData();
        var replacement = event.getEntity().getPersistentData();
        for (String key : original.getAllKeys()) {
            if (key.startsWith("antipiracyjar_") || key.startsWith("AntiPiracy") || key.startsWith("PiracyDream")
                    || key.startsWith("PreDream") || key.startsWith("Struct")) replacement.put(key, original.get(key).copy());
        }
    }

    public record VideoPayload(boolean play) implements CustomPacketPayload {
        public static final Type<VideoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "final_video"));
        public static final StreamCodec<ByteBuf, VideoPayload> CODEC = ByteBufCodecs.BOOL.map(VideoPayload::new, VideoPayload::play);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(VideoPayload.TYPE, VideoPayload.CODEC,
                    (payload, context) -> context.enqueueWork(() -> {
                        if (payload.play()) FinalVideoScreen.open();
                    }));
        }
    }
}

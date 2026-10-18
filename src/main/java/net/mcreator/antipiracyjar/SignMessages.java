package net.mcreator.antipiracyjar;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
import java.util.UUID;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class SignMessages {
    public SignMessages() {
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        new SignMessages();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void clientLoad(FMLClientSetupEvent event) {
    }

    @EventBusSubscriber
    public static class SignMessagesForgeBusEvents {
        @SubscribeEvent
        public static void serverLoad(ServerStartingEvent event) {
        }
    }

    public static void placeCreepySign(ServerPlayer serverPlayer) {
        ServerLevel level = serverPlayer.serverLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        var state = HorrorWorldState.get(serverPlayer.server);
        if (state.data.getBoolean("exitSignDone")) return;
        state.data.putBoolean("exitSignDone", true);
        state.flush(serverPlayer.server);
        serverPlayer.sendSystemMessage(Component.literal("Did you really think that would work?")
                .withStyle(net.minecraft.ChatFormatting.RED).withStyle(style -> style.withBold(false)));
        serverPlayer.playNotifySound(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1, 1);
        Direction opposite = serverPlayer.getDirection().getOpposite();
        BlockPos pos = serverPlayer.blockPosition().relative(opposite);
        if (!level.getBlockState(pos).canBeReplaced()) return;
        int rotation = Mth.floor(serverPlayer.getYRot() * 16.0F / 360.0F + 0.5D) & 15;
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rotation), 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.updateText(text -> text.setMessage(0, Component.literal("I thought"))
                    .setMessage(1, Component.literal("you wanted"))
                    .setMessage(2, Component.literal("to play?")), true);
        }
    }
}

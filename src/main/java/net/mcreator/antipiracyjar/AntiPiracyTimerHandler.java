package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber
public class AntiPiracyTimerHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        CompoundTag persistentData = player.getPersistentData();
        long ticks = persistentData.getLong("AntiPiracyTimer") + 1;
        persistentData.putLong("AntiPiracyTimer", ticks);

        if (ticks == 7000) {
            Component message = Component.literal("████████ ")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("Ignore that. Feel free to continue.").withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(message);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
        } else if (ticks == 9000) {
            player.sendSystemMessage(Component.literal("I see you.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
        } else if (ticks == 10000) {
            player.sendSystemMessage(Component.literal("At your window.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
        } else if (ticks == 13000) {
            player.sendSystemMessage(Component.literal("Don't get up.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
        } else if (ticks == 13200) {
            player.sendSystemMessage(Component.literal("Continue playing.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }
}
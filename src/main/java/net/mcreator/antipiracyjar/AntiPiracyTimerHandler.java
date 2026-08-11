package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

        if (ticks == 8500) {
            Component message = Component.literal("████████ ")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("Ignore that. Feel free to continue.").withStyle(ChatFormatting.WHITE));
            player.sendSystemMessage(message);
        } else if (ticks == 12000) {
            player.sendSystemMessage(Component.literal("I see you.").withStyle(ChatFormatting.RED));
        } else if (ticks == 15000) {
            player.sendSystemMessage(Component.literal("At your window.").withStyle(ChatFormatting.RED));
        } else if (ticks == 17500) {
            player.sendSystemMessage(Component.literal("Don't get up.").withStyle(ChatFormatting.RED));
        } else if (ticks == 19500) {
            player.sendSystemMessage(Component.literal("Continue playing.").withStyle(ChatFormatting.RED));
        }
    }
}
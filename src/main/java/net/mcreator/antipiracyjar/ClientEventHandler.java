package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = "anti_piracyjar",
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME
)
public class ClientEventHandler {
    private static final int[] DELAYS = {
            1200, // [0] До FakeKickScreen (1 минута = 60 * 20) - ИЗМЕНЕНО ДЛЯ ТЕСТА
            400,  // [1] FakeKickScreen -> "Ignore that..." (20 секунд)
            1200, // [2] "Ignore that..." -> "I see you." (1 минута)
            100,  // [3] "I see you." -> "At your window." (5 секунд)
            100,  // [4] "At your window." -> "Don't get up." (5 секунд)
            200   // [5] "Don't get up." -> "Continue playing." (10 секунд)
    };

    private static boolean wasInWorld = false;
    private static int stage = 0;
    private static int ticksRemaining = DELAYS[0];

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            wasInWorld = false;
            stage = 0;
            ticksRemaining = DELAYS[0];
            return;
        }

        if (!wasInWorld) {
            wasInWorld = true;
            stage = 0;
            ticksRemaining = DELAYS[0];
            return;
        }

        if (stage >= DELAYS.length) {
            return;
        }

        if (--ticksRemaining > 0) {
            return;
        }

        runStage(mc, stage);
        stage++;

        if (stage < DELAYS.length) {
            ticksRemaining = DELAYS[stage];
        }
    }

    private static void runStage(Minecraft mc, int currentStage) {
        switch (currentStage) {
            case 0 -> mc.setScreen(new FakeKickScreen());
            case 1 -> {
                Component message = Component.literal("████████ ")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("Ignore that. Feel free to continue.")
                                .withStyle(ChatFormatting.WHITE));

                sendMessage(mc, message);
            }
            case 2 -> sendRedMessage(mc, "I see you.");
            case 3 -> sendRedMessage(mc, "At your window.");
            case 4 -> sendRedMessage(mc, "Don't get up.");
            case 5 -> sendRedMessage(mc, "Continue playing.");
        }
    }

    private static void sendRedMessage(Minecraft mc, String text) {
        sendMessage(mc, Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static void sendMessage(Minecraft mc, Component message) {
        if (mc.player == null) {
            return;
        }

        mc.player.sendSystemMessage(message);
        mc.player.playSound(SoundEvents.AMBIENT_CAVE.value(), 1.0F, 1.0F);
    }
}
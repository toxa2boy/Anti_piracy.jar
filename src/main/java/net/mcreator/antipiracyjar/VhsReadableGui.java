package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class VhsReadableGui {
    private static boolean requested;
    private static int ticksRemaining;

    public static void requestFont(boolean enabled) {
        requested = enabled;
        ticksRemaining = 20;
        if (!enabled) {
            FakeKickScreen.amanoEnabled = false;
        }
    }

    public static void afterWarningClosed() {
        ticksRemaining = 20;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        requested = false;
        FakeKickScreen.amanoEnabled = false;
        ticksRemaining = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen) {
            requested = false;
            FakeKickScreen.amanoEnabled = false;
            return;
        }
        if (!requested || FakeKickScreen.amanoEnabled || minecraft.level == null
                || minecraft.getOverlay() != null || minecraft.screen instanceof FakeKickScreen) {
            return;
        }
        if (ticksRemaining > 0 && --ticksRemaining > 0) {
            return;
        }
        FakeKickScreen.amanoEnabled = true;
        minecraft.gui.getChat().rescaleChat();
        if (minecraft.screen != null) {
            minecraft.screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
        }
    }
}

package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class VhsReadableGui {
    private static boolean pending;

    public static void afterWarningClosed() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new HorrorFontFinishEvent.WarningClosedPayload(true));
        }
    }

    public static void clearChatAndEnable() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().clearMessages(true);
        pending = true;
        applyFont();
    }

    private static void applyFont() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!pending || minecraft.level == null || minecraft.getOverlay() != null
                || minecraft.screen instanceof FakeKickScreen) return;
        if (!AmanoFontController.install()) return;
        pending = false;
        FakeKickScreen.amanoEnabled = true;
        com.mojang.logging.LogUtils.getLogger().info("[Amano] Font enabled after warning; chat cleared");
        minecraft.gui.getChat().rescaleChat();
        if (minecraft.screen != null) {
            minecraft.screen.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        FakeKickScreen.amanoEnabled = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen instanceof TitleScreen) {
            pending = false;
            FakeKickScreen.amanoEnabled = false;
        } else {
            applyFont();
        }
    }
}

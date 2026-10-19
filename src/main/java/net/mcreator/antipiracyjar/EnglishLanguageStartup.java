package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class EnglishLanguageStartup {
    private static int ticks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || minecraft.getOverlay() != null) return;
        if (ticks > 0) {
            ticks--;
            return;
        }
        ticks = 19;
        if (minecraft.options.renderDistance().get() != 9) {
            minecraft.options.renderDistance().set(9);
            minecraft.options.save();
            minecraft.options.broadcastOptions();
            if (minecraft.level != null) minecraft.levelRenderer.allChanged();
        }
        if (!"en_us".equals(minecraft.getLanguageManager().getSelected())) {
            minecraft.getLanguageManager().setSelected("en_us");
            minecraft.getLanguageManager().onResourceManagerReload(minecraft.getResourceManager());
            minecraft.gui.getChat().rescaleChat();
        }
        if (!"en_us".equals(minecraft.options.languageCode)) {
            minecraft.options.languageCode = "en_us";
            minecraft.options.save();
        }
    }
}

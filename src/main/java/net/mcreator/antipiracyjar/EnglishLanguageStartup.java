package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class EnglishLanguageStartup {
    private static boolean applied;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (applied) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || minecraft.getOverlay() != null) {
            return;
        }

        applied = true;
        boolean reload = !"en_us".equals(minecraft.getLanguageManager().getSelected());
        minecraft.getLanguageManager().setSelected("en_us");
        if (!"en_us".equals(minecraft.options.languageCode)) {
            minecraft.options.languageCode = "en_us";
            minecraft.options.save();
        }
        if (reload) {
            minecraft.reloadResourcePacks();
        }
    }
}

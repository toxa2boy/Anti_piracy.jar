package net.mcreator.antipiracyjar;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class FabulousGraphicsStartup {
    private static int ticks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null || minecraft.getOverlay() != null) return;
        if (ticks++ % 20 != 0) return;
        if (minecraft.options.graphicsMode().get() == GraphicsStatus.FABULOUS) return;
        minecraft.options.graphicsMode().set(GraphicsStatus.FABULOUS);
        minecraft.options.save();
        if (minecraft.level != null) minecraft.levelRenderer.allChanged();
    }
}

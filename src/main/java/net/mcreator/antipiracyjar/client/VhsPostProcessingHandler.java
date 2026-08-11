package net.mcreator.antipiracyjar.client;

import net.mcreator.antipiracyjar.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class VhsPostProcessingHandler {

    public static final ResourceLocation VHS_SHADER = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "shaders/post/vhs_horror.json");
    public static boolean loaded = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.enableShader) {
            if (loaded && minecraft.gameRenderer != null) {
                minecraft.gameRenderer.shutdownEffect();
                loaded = false;
            }
            return;
        }
        if (minecraft.level != null && minecraft.player != null) {
            if (!loaded || minecraft.gameRenderer.currentEffect() == null) {
                minecraft.gameRenderer.loadEffect(VHS_SHADER);
                loaded = true;
            }
        } else {
            loaded = false;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!ClientConfig.enableShader) {
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_F4 || event.getKey() == GLFW.GLFW_KEY_F5) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                loaded = false;
                minecraft.gameRenderer.loadEffect(VHS_SHADER);
                loaded = true;
            }
        }
    }
}
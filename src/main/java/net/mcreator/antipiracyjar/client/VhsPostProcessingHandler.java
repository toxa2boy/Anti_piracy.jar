package net.mcreator.antipiracyjar.client;

import com.mojang.blaze3d.platform.InputConstants;
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
        if (minecraft.level != null && minecraft.player != null) {
            if (!loaded) {
                minecraft.gameRenderer.loadEffect(VHS_SHADER);
                loaded = true;
            }
        } else {
            loaded = false;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_F4) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                loaded = false;
                minecraft.gameRenderer.loadEffect(VHS_SHADER);
                loaded = true;
            }
        }
    }
}
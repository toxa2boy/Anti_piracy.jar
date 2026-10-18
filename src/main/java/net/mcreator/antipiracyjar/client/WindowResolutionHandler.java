package net.mcreator.antipiracyjar.client;

import com.mojang.blaze3d.platform.Window;
import net.mcreator.antipiracyjar.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class WindowResolutionHandler {

    private static final int LOCKED_WIDTH = 800;
    private static final int LOCKED_HEIGHT = 600;

    private static boolean wasLocked = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        if (window == null) return;

        long handle = window.getWindow();
        if (handle == MemoryUtil.NULL) return;

        if (!ClientConfig.lockResolution) {
            if (wasLocked) {
                unlock(handle);
                wasLocked = false;
            }
            return;
        }

        boolean isFullscreen = GLFW.glfwGetWindowMonitor(handle) != MemoryUtil.NULL;
        int currentWidth = window.getScreenWidth();
        int currentHeight = window.getScreenHeight();


        if (!isFullscreen || currentWidth != LOCKED_WIDTH || currentHeight != LOCKED_HEIGHT) {
            lockToResolution(handle);
        }

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        wasLocked = true;
    }

    private static void lockToResolution(long handle) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) return;
        GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, LOCKED_WIDTH, LOCKED_HEIGHT, GLFW.GLFW_DONT_CARE);
    }

    private static void unlock(long handle) {
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowMonitor(handle, MemoryUtil.NULL, 100, 100, 1280, 720, GLFW.GLFW_DONT_CARE);
    }
}
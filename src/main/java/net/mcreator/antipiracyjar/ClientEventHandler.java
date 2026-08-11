package net.mcreator.antipiracyjar;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientEventHandler {
    private static boolean wasInWorld = false;
    private static int timer = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            if (!wasInWorld) {
                wasInWorld = true;
                timer = 100;
            } else if (timer > 0) {
                timer--;
                if (timer == 0) {
                    mc.setScreen(new FakeKickScreen());
                    timer = -1;
                }
            }
        } else {
            wasInWorld = false;
            timer = -1;
        }
    }
}
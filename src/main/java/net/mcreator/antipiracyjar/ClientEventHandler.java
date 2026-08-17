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
        
        // Проверяем, что игрок находится в мире
        if (mc.player != null && mc.level != null) {
            if (!wasInWorld) {
                wasInWorld = true;
                // 6000 тиков = 5 минут (20 тиков в секунду * 60 секунд * 5)
                timer = 6000; 
            } else if (timer > 0) {
                timer--;
                if (timer == 0) {
                    // Время вышло - показываем фейковый экран кика
                    mc.setScreen(new FakeKickScreen());
                    timer = -1;
                }
            }
        } else {
            // Сбрасываем таймер, если игрок вышел из мира
            wasInWorld = false;
            timer = -1;
        }
    }
}
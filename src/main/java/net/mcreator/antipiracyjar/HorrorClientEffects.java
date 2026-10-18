package net.mcreator.antipiracyjar;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class HorrorClientEffects {
    public static int sequence;
    public static long whiteout;
    public static void receive(int effect) {
        if (effect == 99) { Minecraft.getInstance().stop(); return; }
        if (effect == 0) Minecraft.getInstance().setScreen(new FakeKickScreen());
        if (effect >= 1000 && effect <= 1181) sequence = effect - 1000;
        if (effect == 11 && whiteout == 0) whiteout = System.currentTimeMillis();
        if (effect == 12) whiteout = 0;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (sequence > 0 && ++sequence > 181) sequence = 0;
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { sequence = 0; whiteout = 0; }
}

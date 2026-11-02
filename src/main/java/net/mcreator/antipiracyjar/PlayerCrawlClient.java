package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="anti_piracyjar",value=Dist.CLIENT)
public final class PlayerCrawlClient {
    private static final KeyMapping CRAWL=new KeyMapping("key.anti_piracyjar.crawl",org.lwjgl.glfw.GLFW.GLFW_KEY_Z,"key.categories.anti_piracyjar");
    private static boolean active;

    @EventBusSubscriber(modid="anti_piracyjar",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) { event.register(CRAWL); }
    }

    public static void state(boolean crawling) {
        var player=Minecraft.getInstance().player;
        if(player!=null) {
            if(crawling) { player.setForcedPose(Pose.SWIMMING);player.setPose(Pose.SWIMMING);player.setSprinting(false); }
            else if(active && player.getForcedPose()==Pose.SWIMMING) player.setForcedPose(null);
        }
        active=crawling;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        while(CRAWL.consumeClick()) {
            if(mc.player!=null && mc.level!=null && mc.screen==null && !mc.isPaused() && mc.getConnection()!=null)
                PacketDistributor.sendToServer(new HorrorNetwork.CrawlTogglePayload(true));
        }
        if(active && mc.player!=null) {
            if(!mc.player.isAlive()) { state(false);return; }
            mc.player.setForcedPose(Pose.SWIMMING);
            mc.player.setSprinting(false);
        }
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if(active) event.getInput().jumping=false;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { state(false); }
}

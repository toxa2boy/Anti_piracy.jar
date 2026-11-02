package net.mcreator.antipiracyjar;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="anti_piracyjar")
public final class PlayerCrawlEvent {
    private static final Set<UUID> ACTIVE=new HashSet<>();
    private static final ResourceLocation SPEED=ResourceLocation.fromNamespaceAndPath("anti_piracyjar","player_crawl_speed");
    private static final AttributeModifier SLOW=new AttributeModifier(SPEED,-.55,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static boolean allowed(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isPassenger()
            && !player.isInWater() && !player.isInLava() && !player.isFallFlying() && !player.getAbilities().flying
            && !HorrorDirector.owns(player,"ground_grab") && !HorrorDirector.owns(player,"turnaround");
    }

    private static void send(ServerPlayer player,boolean crawling) {
        PacketDistributor.sendToPlayer(player,new HorrorNetwork.CrawlStatePayload(crawling));
    }

    public static void toggle(ServerPlayer player) {
        if(ACTIVE.contains(player.getUUID())) {
            var standing=player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7);
            if(!player.level().noCollision(player,standing)) {
                player.displayClientMessage(Component.literal("Not enough room to stand."),true);
                send(player,true);return;
            }
            stop(player);return;
        }
        if(!allowed(player) || !player.onGround() || player.getForcedPose()!=null) { send(player,false);return; }
        ACTIVE.add(player.getUUID());
        apply(player);send(player,true);
    }

    private static void apply(ServerPlayer player) {
        player.setForcedPose(Pose.SWIMMING);
        player.setPose(Pose.SWIMMING);
        player.setSprinting(false);
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null && !speed.hasModifier(SPEED)) speed.addTransientModifier(SLOW);
    }

    private static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        if(player.getForcedPose()==Pose.SWIMMING) player.setForcedPose(null);
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null) speed.removeModifier(SPEED);
        send(player,false);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();
        for(UUID id:java.util.List.copyOf(ACTIVE)) {
            var player=server.getPlayerList().getPlayer(id);
            if(player==null) { ACTIVE.remove(id);continue; }
            if(!allowed(player)) { stop(player);continue; }
            apply(player);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && ACTIVE.contains(player.getUUID())) stop(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && ACTIVE.contains(player.getUUID())) stop(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}

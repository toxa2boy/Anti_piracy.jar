package net.mcreator.antipiracyjar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class DontTurnAroundEvent {
    public static final int EFFECT = 21;
    private static final Map<UUID, Long> ARMED = new HashMap<>();
    private static final Map<UUID, Freeze> FROZEN = new HashMap<>();
    private record Freeze(Vec3 position, long until) {}

    public static void scare(ServerPlayer player) {
        Long deadline = ARMED.remove(player.getUUID());
        long now = player.server.overworld().getGameTime();
        if (deadline == null || now > deadline || !player.isAlive() || player.isSpectator()
                || player.level().dimension() != Level.OVERWORLD) return;
        player.stopRiding();
        FROZEN.put(player.getUUID(), new Freeze(player.position(), now + 60));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true, true));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PlayerEventCooldowns.remaining(player, "turnaround", 360, 600);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ARMED.remove(event.getEntity().getUUID());
        FROZEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ARMED.clear();
        FROZEN.clear();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        ARMED.entrySet().removeIf(e -> now > e.getValue());
        var freezes = FROZEN.entrySet().iterator();
        while (freezes.hasNext()) {
            var entry = freezes.next();
            var player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            var freeze = entry.getValue();
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.level().dimension() != Level.OVERWORLD || now >= freeze.until()) {
                freezes.remove();
                continue;
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.hurtMarked = true;
            if (player.position().distanceToSqr(freeze.position()) > .0001)
                player.connection.teleport(freeze.position().x, freeze.position().y, freeze.position().z,
                        player.getYRot(), player.getXRot());
        }
        if (now % 20 != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD
                    || busy(player.getUUID())) continue;
            if (HorrorDirector.roll(player, "turnaround", 32, 480, 1800)
                    && HorrorDirector.start(player,"turnaround",560,false)) {
                PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(EFFECT));
                ARMED.put(player.getUUID(), now + 420);
                PlayerEventCooldowns.reset(player, "turnaround", 360, 600);
            }
        }
    }

    public static boolean busy(UUID player) {
        return ARMED.containsKey(player) || FROZEN.containsKey(player);
    }
}

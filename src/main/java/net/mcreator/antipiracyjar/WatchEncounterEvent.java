package net.mcreator.antipiracyjar;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class WatchEncounterEvent {
    public static final int EFFECT = 22;
    public static final int SCARE = 23;
    public static final int TEST = 24;
    private static final java.util.Map<UUID, Encounter> ACTIVE = new java.util.HashMap<>();
    private static class Encounter {
        long expires;
        boolean started;
        Encounter(long expires) { this.expires = expires; }
    }

    public static boolean busy(UUID player) { return ACTIVE.containsKey(player); }

    private static void finish(ServerPlayer player) {
        var encounter = ACTIVE.remove(player.getUUID());
        if (encounter != null) HorrorDirector.finish(player,"watch",encounter.started);
    }

    public static void receive(ServerPlayer player, int action) {
        var encounter = ACTIVE.get(player.getUUID());
        if (encounter == null) return;
        long now = player.server.overworld().getGameTime();
        if (now > encounter.expires || !player.isAlive() || player.isSpectator()
                || player.level().dimension() != Level.OVERWORLD) { finish(player); return; }
        if (action == 1) {
            if (!encounter.started) {
                encounter.started = true;
                PlayerEventCooldowns.reset(player, "watch", 180, 480);
            }
            encounter.expires = now + 600;
            HorrorDirector.renew(player,"watch",640);
        } else if (action == 0 || action == 2) {
            finish(player);
        } else if (action == 3 && encounter.started) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, true));
            player.sendSystemMessage(Component.literal("You shouldn't have come closer."));
            PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(SCARE));
            finish(player);
        }
    }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        for (var player : server.getPlayerList().getPlayers()) {
            var encounter = ACTIVE.get(player.getUUID());
            if (encounter != null) {
                if (now > encounter.expires || !player.isAlive() || player.isSpectator()
                        || player.level().dimension() != Level.OVERWORLD) finish(player);
                continue;
            }
            if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) continue;
            if (!HorrorDirector.roll(player,"watch",7,600,1800)
                    || !HorrorDirector.start(player,"watch",140,false)) continue;
            ACTIVE.put(player.getUUID(), new Encounter(now + 100));
            PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(EFFECT));
        }
    }

    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("watchtest")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("status").executes(context -> {
                    var server = context.getSource().getServer();
                    var player = context.getSource().getPlayerOrException();
                    long ticks = PlayerEventCooldowns.remaining(player, "watch", 180, 480);
                    var encounter = ACTIVE.get(player.getUUID());
                    String status = encounter != null ? (encounter.started ? "Watch encounter is active." : "Watch is choosing a location.")
                            : "Watch uses random pacing. Use /horrorpacing for the global scene and quiet period.";
                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("spawn").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (busy(player.getUUID()) || DontTurnAroundEvent.busy(player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("A Watch encounter is already active. Wait until it ends."));
                        return 0;
                    }
                    if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
                        context.getSource().sendFailure(Component.literal("Use this command alive in the Overworld, outside spectator mode."));
                        return 0;
                    }
                    if (!HorrorDirector.start(player,"watch",140,true)) {
                        context.getSource().sendFailure(Component.literal("Another horror scene is active.")); return 0;
                    }
                    ACTIVE.put(player.getUUID(), new Encounter(player.server.overworld().getGameTime() + 100));
                    PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(TEST));
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}

package net.mcreator.antipiracyjar;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public class AmbientEvent {
    private static final int COOLDOWN_TICKS = 6 * 60 * 20;

    private static final Holder<SoundEvent> AMBIENT_SOUND = Holder.direct(
            SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "ambient")
            )
    );

    private static int ticksRemaining = COOLDOWN_TICKS;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ticksRemaining = COOLDOWN_TICKS;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getPlayerList().getPlayerCount() == 0) {
            return;
        }

        if (ticksRemaining > 0) {
            ticksRemaining--;
        }

        if (ticksRemaining > 0) {
            return;
        }

        List<ServerPlayer> players = event.getServer()
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .toList();

        if (players.isEmpty()) {
            return;
        }

        RandomSource random = event.getServer().overworld().getRandom();
        ServerPlayer player = players.get(random.nextInt(players.size()));

        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 5.0 + random.nextDouble() * 5.0;

        double x = player.getX() + Math.cos(angle) * distance;
        double y = player.getEyeY();
        double z = player.getZ() + Math.sin(angle) * distance;

        player.connection.send(new ClientboundSoundPacket(
                AMBIENT_SOUND,
                SoundSource.AMBIENT,
                x,
                y,
                z,
                1.0F,
                1.0F,
                random.nextLong()
        ));

        ticksRemaining = COOLDOWN_TICKS;
    }
}
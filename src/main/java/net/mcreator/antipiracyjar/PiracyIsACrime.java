package net.mcreator.antipiracyjar;

import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class PiracyIsACrime {
    private static final Map<UUID, Integer> playerTicks = new HashMap<>();

    public PiracyIsACrime() {
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        new PiracyIsACrime();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void clientLoad(FMLClientSetupEvent event) {
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
    private static class PiracyIsACrimeForgeBusEvents {
        @SubscribeEvent
        public static void serverLoad(ServerStartingEvent event) {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                UUID uuid = player.getUUID();
                int ticks = playerTicks.getOrDefault(uuid, 0) + 1;

                if (ticks >= 5900 && ticks < 6000) {
                    if (ticks == 5901) {
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 2, false, false, false));
                        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 2, false, false, false));
                        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 10, 0));
                    }

                    if (ticks % 3 == 0) {
                        float yaw = player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * 70f;
                        float pitch = player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * 70f;
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), yaw, pitch);
                    }

                    if (ticks % 8 == 0) {
                        player.playNotifySound(SoundEvents.MINECART_RIDING, SoundSource.MASTER, 1.0f, player.getRandom().nextFloat() * 1.5f + 0.5f);
                    }

                    if (ticks == 5920 || ticks == 5970) {
                        player.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.MASTER, 0.8f, 0.1f);
                    }

                    if (ticks % 5 == 0) {
                        String garble = "!#$" + player.getRandom().nextInt(999);
                        Component glitchText = Component.literal(garble + " ")
                                .append(Component.literal("FATAL ERROR").withStyle(ChatFormatting.OBFUSCATED))
                                .withStyle(ticks % 10 == 0 ? ChatFormatting.DARK_RED : ChatFormatting.RED, ChatFormatting.BOLD);
                        player.connection.send(new ClientboundSetTitleTextPacket(glitchText));
                    }

                    playerTicks.put(uuid, ticks);
                } else if (ticks >= 6000) {
                    Component message = Component.literal("Piracy is a crime!\n\n")
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)
                        .append(Component.literal("The game you possess is an illegal or corrupted copy. Please uninstall the program immediately and purchase the official licensed edition from ").withStyle(ChatFormatting.RESET, ChatFormatting.GRAY))
                        .append(Component.literal("www.minecraft.net").withStyle(ChatFormatting.DARK_RED, ChatFormatting.STRIKETHROUGH));

                    player.connection.disconnect(message);
                    playerTicks.remove(uuid);
                } else {
                    playerTicks.put(uuid, ticks);
                }
            }
        }
    }
}
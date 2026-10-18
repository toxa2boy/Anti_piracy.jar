package net.mcreator.antipiracyjar;

import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public class HorrorFontFinishEvent {
    private static final Set<UUID> handled = new HashSet<>();
    private static int remaining = -1;

    public record WarningClosedPayload(boolean closed) implements CustomPacketPayload {
        public static final Type<WarningClosedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "warning_closed"));
        public static final StreamCodec<ByteBuf, WarningClosedPayload> CODEC = ByteBufCodecs.BOOL.map(WarningClosedPayload::new, WarningClosedPayload::closed);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FontFinishPayload(boolean activate) implements CustomPacketPayload {
        public static final Type<FontFinishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "font_finish"));
        public static final StreamCodec<ByteBuf, FontFinishPayload> CODEC = ByteBufCodecs.BOOL.map(FontFinishPayload::new, FontFinishPayload::activate);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
    public static class NetworkEvents {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(WarningClosedPayload.TYPE, WarningClosedPayload.CODEC,
                    (payload, context) -> context.enqueueWork(() -> {
                        if (payload.closed() && context.player() instanceof ServerPlayer player) {
                            var state = HorrorWorldState.get(player.server);
                            if (state.data.getBoolean("warning_" + player.getUUID()) && !state.data.getBoolean("fontEnabled")) {
                                long due = player.server.overworld().getGameTime() + 200;
                                long previous = state.data.getLong("fontDue");
                                if (previous == 0 || due < previous) {
                                    state.data.putLong("fontDue", due);
                                    state.setDirty();
                                }
                            }
                        }
                    }));
            event.registrar("1").playToClient(FontFinishPayload.TYPE, FontFinishPayload.CODEC,
                    (payload, context) -> context.enqueueWork(() -> {
                        if (payload.activate()) VhsReadableGui.clearChatAndEnable();
                    }));
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var state = HorrorWorldState.get(event.getServer());
        long due = state.data.getLong("fontDue");
        if (!state.data.getBoolean("fontEnabled") && due > 0 && event.getServer().overworld().getGameTime() >= due) {
            state.data.putBoolean("fontEnabled", true);
            state.flush(event.getServer());
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(player, new FontFinishPayload(true));
            }
        }
    }

    @SubscribeEvent
    public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HorrorWorldState.get(player.server).data.getBoolean("fontEnabled")) {
            PacketDistributor.sendToPlayer(player, new FontFinishPayload(true));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        remaining = -1;
        handled.clear();
    }
}

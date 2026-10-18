package net.mcreator.antipiracyjar;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
public final class HorrorNetwork {
    public record EffectPayload(int effect) implements CustomPacketPayload {
        public static final Type<EffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "story_effect"));
        public static final StreamCodec<ByteBuf, EffectPayload> CODEC = ByteBufCodecs.VAR_INT.map(EffectPayload::new, EffectPayload::effect);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ExitPayload(boolean requested) implements CustomPacketPayload {
        public static final Type<ExitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "exit_sign"));
        public static final StreamCodec<ByteBuf, ExitPayload> CODEC = ByteBufCodecs.BOOL.map(ExitPayload::new, ExitPayload::requested);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ExitPayload.TYPE, ExitPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (payload.requested() && context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        SignMessages.placeCreepySign(player);
                }));
        event.registrar("1").playToClient(EffectPayload.TYPE, EffectPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> HorrorClientEffects.receive(payload.effect())));
    }
}

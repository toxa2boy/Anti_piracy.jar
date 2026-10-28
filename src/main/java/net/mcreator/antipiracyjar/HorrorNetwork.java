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
        event.registrar("1").playToServer(UiVisitPayload.TYPE,UiVisitPayload.CODEC,
                (payload,context)->context.enqueueWork(()->{
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) UiVisitGate.receive(player,payload.action());
                }));
        event.registrar("1").playToServer(KnockStatePayload.TYPE, KnockStatePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        DrunSleepEvent.receive(player,payload.action());
                }));
        event.registrar("1").playToClient(KnockPayload.TYPE, KnockPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> DrunVisitClient.knock(payload)));
        event.registrar("1").playToServer(WatchStatePayload.TYPE, WatchStatePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        WatchEncounterEvent.receive(player, payload.action());
                }));
        event.registrar("1").playToServer(ScarePayload.TYPE, ScarePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (payload.triggered() && context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        DontTurnAroundEvent.scare(player);
                }));
        event.registrar("1").playToServer(ExitPayload.TYPE, ExitPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (payload.requested() && context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        SignMessages.placeCreepySign(player);
                }));
        event.registrar("1").playToClient(EffectPayload.TYPE, EffectPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> HorrorClientEffects.receive(payload.effect())));
    }
    public record ScarePayload(boolean triggered) implements CustomPacketPayload {
        public static final Type<ScarePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "turnaround_scare"));
        public static final StreamCodec<ByteBuf, ScarePayload> CODEC = ByteBufCodecs.BOOL.map(ScarePayload::new, ScarePayload::triggered);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record WatchStatePayload(int action) implements CustomPacketPayload {
        public static final Type<WatchStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "watch_state"));
        public static final StreamCodec<ByteBuf, WatchStatePayload> CODEC = ByteBufCodecs.VAR_INT.map(WatchStatePayload::new, WatchStatePayload::action);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record KnockPayload(net.minecraft.core.BlockPos target, net.minecraft.core.BlockPos feet,
            java.util.List<net.minecraft.core.BlockPos> route, int voice, boolean stop) implements CustomPacketPayload {
        public static final Type<KnockPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_knock"));
        public static final StreamCodec<ByteBuf, KnockPayload> CODEC = StreamCodec.composite(
                net.minecraft.core.BlockPos.STREAM_CODEC, KnockPayload::target,
                net.minecraft.core.BlockPos.STREAM_CODEC, KnockPayload::feet,
                net.minecraft.core.BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(32)), KnockPayload::route,
                ByteBufCodecs.VAR_INT, KnockPayload::voice,
                ByteBufCodecs.BOOL, KnockPayload::stop, KnockPayload::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record KnockStatePayload(int action) implements CustomPacketPayload {
        public static final Type<KnockStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "drun_knock_state"));
        public static final StreamCodec<ByteBuf, KnockStatePayload> CODEC = ByteBufCodecs.VAR_INT.map(KnockStatePayload::new, KnockStatePayload::action);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record UiVisitPayload(int action) implements CustomPacketPayload {
        public static final Type<UiVisitPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anti_piracyjar","ui_visit"));
        public static final StreamCodec<ByteBuf,UiVisitPayload> CODEC=ByteBufCodecs.VAR_INT.map(UiVisitPayload::new,UiVisitPayload::action);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}

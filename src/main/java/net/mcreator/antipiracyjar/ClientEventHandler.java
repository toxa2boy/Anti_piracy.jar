package net.mcreator.antipiracyjar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid = "anti_piracyjar")
public final class ClientEventHandler {
    static final long[] TIMES = {12000, 23999, 30000, 42000, 48000, 60000};
    private static final String[] TEXT = {"", "Ignore that. Feel free to continue.", "I see you.", "At your window.", "Don't get up.", "Continue playing."};
    public static List<ServerPlayer> targets(MinecraftServer server) {
        return targets(server, server.getPlayerList().getPlayers());
    }
    static List<ServerPlayer> targets(MinecraftServer server, List<ServerPlayer> players) {
        List<ServerPlayer> candidates = new ArrayList<>(players.stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && p.level().dimension().equals(Level.OVERWORLD)
).toList());
        List<ServerPlayer> chosen = new ArrayList<>();
        var random = server.overworld().random;
        int count = Math.min(candidates.size(), 1 + random.nextInt(2));
        for (int i = 0; i < count; i++) chosen.add(candidates.remove(random.nextInt(candidates.size())));
        return chosen;
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.overworld().getGameTime() % 20 != 0) return;
        var state = HorrorWorldState.get(server);
        int stage = state.data.getInt("storyStage");
        if (stage >= TIMES.length) return;
        long day = server.overworld().getDayTime();
        if (HorrorDirector.clock(server) < HorrorDirector.storyAt(server,stage)) return;
        List<ServerPlayer> players = targets(server);
        if (players.isEmpty() || !HorrorDirector.start(server,"story","",stage==0 ? 400 : 80,false)) return;
        state.data.putInt("storyStage", stage + 1);
        state.data.putLong("nextStoryTick", server.overworld().getGameTime() + 200);
        if (stage == 0) {
            state.data.putLong("fontDue", server.overworld().getGameTime() + 320);
            for (ServerPlayer player : players) state.data.putBoolean("warning_" + player.getUUID(), true);
        }
        if (stage == 5) {
            state.data.putBoolean("messagesFinished", true);
            state.data.putLong("messagesFinishedDay", day / 24000);
        }
        state.flush(server);
        for (ServerPlayer player : players) {
            if (stage == 0) PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(0));
            else {
                player.sendSystemMessage(Component.literal(TEXT[stage])
                        .withStyle(stage == 1 ? ChatFormatting.WHITE : ChatFormatting.RED)
                        .withStyle(style -> style.withBold(false)));
                player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1, 1);
            }
        }
    }
}

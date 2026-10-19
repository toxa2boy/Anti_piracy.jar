package net.mcreator.antipiracyjar;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class OverworldOmenEvents {
    static final long LIGHTNING_TIME = 9 * 60 * 20;
    static final String[] NAMED = {"%s died", "%s drowned", "%s fell from a high place", "%s suffocated in a wall", "%s was lost in the dark", "%s never made it home"};
    static final String[] ANONYMOUS = {"Something fell.", "Something drowned.", "Someone stopped breathing.", "Something died behind the wall.", "Someone did not come back.", "Something was buried alive."};

    static long nextDelay(net.minecraft.util.RandomSource random) { return 6000L + random.nextInt(12001); }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var level = server.overworld();
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        List<ServerPlayer> players = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()
                && !p.isCreative() && !LoginHorrorEvent.isPending(p) && !StoneHuntEvent.blocked(server, p.getUUID())).toList();
        if (players.isEmpty()) return;
        var state = HorrorWorldState.get(server);
        if (!state.data.getBoolean("omenLightningDone") && now >= LIGHTNING_TIME) {
            var target = players.get(level.random.nextInt(players.size()));
            var bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
                if (level.addFreshEntity(bolt)) {
                    state.data.putBoolean("omenLightningDone", true);
                    state.flush(server);
                }
            }
        }
        if (!state.data.contains("omenChatNext")) {
            state.data.putLong("omenChatNext", now + nextDelay(level.random));
            state.setDirty();
            return;
        }
        if (now < state.data.getLong("omenChatNext")) return;
        String text;
        if (level.random.nextBoolean()) {
            var target = players.get(level.random.nextInt(players.size()));
            text = NAMED[level.random.nextInt(NAMED.length)].formatted(target.getGameProfile().getName());
        } else text = ANONYMOUS[level.random.nextInt(ANONYMOUS.length)];
        state.data.putLong("omenChatNext", now + nextDelay(level.random));
        state.flush(server);
        Component message = Component.literal(text);
        for (var player : level.players()) player.sendSystemMessage(message);
    }
}

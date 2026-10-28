package net.mcreator.antipiracyjar;

import net.minecraft.server.level.ServerPlayer;

public final class PlayerEventCooldowns {
    public static long remaining(ServerPlayer player, String event, int minSeconds, int maxSeconds) {
        var state = HorrorWorldState.get(player.server);
        String key = "personalCooldown_" + event + "_" + player.getUUID();
        if (!state.data.contains(key)) reset(player, event, minSeconds, maxSeconds);
        return state.data.getLong(key);
    }

    public static void reset(ServerPlayer player, String event, int minSeconds, int maxSeconds) {
        var state = HorrorWorldState.get(player.server);
        String key = "personalCooldown_" + event + "_" + player.getUUID();
        state.data.putLong(key, 20L * (minSeconds + player.getRandom().nextInt(maxSeconds - minSeconds + 1)));
        state.setDirty();
    }

    public static boolean advance(ServerPlayer player, String event, int minSeconds, int maxSeconds) {
        long ticks = remaining(player, event, minSeconds, maxSeconds);
        if (ticks > 0) {
            var state = HorrorWorldState.get(player.server);
            ticks = Math.max(0, ticks - 20);
            state.data.putLong("personalCooldown_" + event + "_" + player.getUUID(), ticks);
            state.setDirty();
        }
        return ticks == 0;
    }
}

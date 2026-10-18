package net.mcreator.antipiracyjar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.mcreator.antipiracyjar.entity.DrunEntity;
import net.mcreator.antipiracyjar.init.AntiPiracyjarModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "anti_piracyjar")
public final class StoneHuntEvent {
    static final String HUNTER = "stoneHunter";
    public static final String LOCK_MESSAGE = "Your access to this world has been revoked.";
    private static final Map<UUID, Integer> CLOSING = new HashMap<>();

    public static boolean blocked(MinecraftServer server, UUID player) {
        return HorrorWorldState.get(server).data.getBoolean("stoneLocked_" + player);
    }

    static void markDefeated(MinecraftServer server, UUID player) {
        var state = HorrorWorldState.get(server);
        state.data.putBoolean("stoneLocked_" + player, true);
        state.flush(server);
    }

    private static boolean stone(ServerLevel level) {
        return level.dimension().location().toString().equals("anti_piracyjar:kamniizmerenie");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof DrunEntity mob)) return;
        var state = HorrorWorldState.get(level.getServer());
        if (state.data.hasUUID(HUNTER) && !state.data.getUUID(HUNTER).equals(mob.getUUID())) {
            event.setCanceled(true);
            return;
        }
        mob.setPersistenceRequired();
        mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
        if (!state.data.hasUUID(HUNTER)) {
            state.data.putUUID(HUNTER, mob.getUUID());
            state.flush(level.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        var state = HorrorWorldState.get(level.getServer());
        if (event.getEntity() instanceof DrunEntity mob && state.data.hasUUID(HUNTER)
                && state.data.getUUID(HUNTER).equals(mob.getUUID())) {
            state.data.remove(HUNTER);
            for (String key : new ArrayList<>(state.data.getAllKeys()))
                if (key.startsWith("stoneTime_")) state.data.remove(key);
            state.flush(level.getServer());
        }
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() instanceof DrunEntity) {
            markDefeated(player.server, player.getUUID());
            PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(99));
            CLOSING.put(player.getUUID(), 40);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && blocked(player.server, player.getUUID()))
            player.connection.disconnect(Component.literal(LOCK_MESSAGE));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        CLOSING.entrySet().removeIf(entry -> {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            int left = entry.getValue() - 1;
            if (left <= 0) { player.connection.disconnect(Component.literal(LOCK_MESSAGE)); return true; }
            entry.setValue(left);
            return false;
        });
        if (server.overworld().getGameTime() % 20 != 0) return;
        var state = HorrorWorldState.get(server);
        if (state.data.hasUUID(HUNTER)) {
            for (var level : server.getAllLevels()) {
                if (!(level.getEntity(state.data.getUUID(HUNTER)) instanceof DrunEntity mob) || !mob.isAlive()) continue;
                var target = level.players().stream().filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                        && !blocked(server, p.getUUID()) && mob.distanceToSqr(p) <= 64 * 64)
                        .min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
                mob.setTarget(target);
            }
            return;
        }
        var ready = new ArrayList<ServerPlayer>();
        for (var player : server.getPlayerList().getPlayers()) {
            if (!stone(player.serverLevel()) || !player.isAlive() || player.isCreative() || player.isSpectator()
                    || blocked(server, player.getUUID())) continue;
            String key = "stoneTime_" + player.getUUID();
            int seconds = Math.min(120, state.data.getInt(key) + 1);
            state.data.putInt(key, seconds);
            state.setDirty();
            if (seconds >= 120) ready.add(player);
        }
        if (ready.isEmpty() || server.overworld().getGameTime() % 100 != 0) return;
        var player = ready.get(server.overworld().random.nextInt(ready.size()));
        spawnNear(player);
    }

    static boolean spawnNear(ServerPlayer player) {
        var level = player.serverLevel();
        if (HorrorWorldState.get(player.server).data.hasUUID(HUNTER)) return false;
        var mob = AntiPiracyjarModEntities.DRUN.get().create(level);
        if (mob == null) return false;
        int cx = Math.floorDiv(player.getBlockX(), 48), cz = Math.floorDiv(player.getBlockZ(), 48);
        var rooms = new ArrayList<StoneWorldLayout.Room>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            rooms.add(new StoneWorldLayout.Room(cx + dx, cz + dz, level.getSeed()));
        rooms.sort(Comparator.comparingDouble(r -> player.distanceToSqr(r.x, r.floor + 1, r.z)));
        for (var room : rooms) {
            for (int attempt = 0; attempt < 8; attempt++) {
                var pos = new BlockPos(room.x + level.random.nextInt(9) - 4, room.floor + 1,
                        room.z + level.random.nextInt(9) - 4);
                double distance = player.distanceToSqr(Vec3.atBottomCenterOf(pos));
                if (distance < 18 * 18 || distance > 72 * 72 || !level.hasChunkAt(pos)) continue;
                if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
                mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.random.nextFloat() * 360, 0);
                if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox()) || mob.hasLineOfSight(player)) continue;
                mob.setPersistenceRequired();
                mob.setTarget(player);
                if (level.addFreshEntity(mob)) return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { CLOSING.clear(); }
}

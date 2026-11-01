package net.mcreator.antipiracyjar;

import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber
public class StoneBedWatchEvent {

    private static final int DELAY_TICKS = 10 * 20;
    private static final boolean DEBUG = true;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static void dbg(String message, Object... args) {
        if (DEBUG) {
            LOGGER.info("[StoneBedWatch] " + message, args);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag data = player.getPersistentData();
        if (!data.contains(StoneBedUseEvent.SESSION_KEY)) return;

        CompoundTag session = data.getCompound(StoneBedUseEvent.SESSION_KEY);
        int kind = session.getInt("entity");
        if (kind <= 0) return;

        int elapsed = StoneBedUseEvent.SESSION_TICKS - session.getInt("ticks");
        if (elapsed < DELAY_TICKS) return;

        // Сбрасываем флаг, чтобы не триггерить повторно в этой же сессии
        session.putInt("entity", 0);
        data.put(StoneBedUseEvent.SESSION_KEY, session);

        boolean started = start(player);
        dbg("entity stage triggered: {}", started ? "started" : "skipped");
    }

    static boolean start(ServerPlayer player) {
        if (WatchEncounterEvent.busy(player.getUUID()) || DontTurnAroundEvent.busy(player.getUUID())) return false;
        if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) return false;
        if (!HorrorDirector.start(player, "watch", 140, true)) return false;

        try {
            register(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[StoneBedWatch] could not register the encounter in WatchEncounterEvent", e);
            HorrorDirector.finish(player, "watch", false);
            return false;
        }

        PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(WatchEncounterEvent.EFFECT));
        return true;
    }

    public static void cancel(ServerPlayer player) {
        WatchEncounterEvent.receive(player, 0);
    }

    @SuppressWarnings("unchecked")
    private static void register(ServerPlayer player) throws ReflectiveOperationException {
        Field active = WatchEncounterEvent.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        Map<UUID, Object> map = (Map<UUID, Object>) active.get(null);

        Class<?> encounterClass = Class.forName(WatchEncounterEvent.class.getName() + "$Encounter");
        Constructor<?> constructor = encounterClass.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);

        map.put(player.getUUID(), constructor.newInstance(player.server.overworld().getGameTime() + 100));
    }
}
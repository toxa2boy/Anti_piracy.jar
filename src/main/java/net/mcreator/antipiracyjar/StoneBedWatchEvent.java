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

/**
 * Второй скрипт для "сна" в структуре (StoneBedUseEvent): встреча Watch.
 *
 * WatchEncounterEvent при этом НЕ изменён: здесь повторена логика его запуска (как в /watchtest spawn,
 * только с обычным эффектом EFFECT), а сама встреча заносится в его внутренний список ACTIVE.
 * Поэтому дальше её ведёт сам WatchEncounterEvent (ответы клиента, ослепление, завершение).
 *
 * Когда запускается: StoneBedUseEvent помечает сессию флагом "script" (попаданий в структуры уже больше порога).
 * Здесь через 10 секунд после начала сессии флаг снимается и встреча стартует.
 * Если игрок вернулся в каменный мир раньше, сессия (и флаг) удалены - встреча не стартует; идущая встреча прерывается.
 */
@EventBusSubscriber
public class StoneBedWatchEvent {

    /** Через сколько тиков после начала "сна" запускается встреча: 10 секунд. */
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
        if (!session.getBoolean("script")) return;

        int elapsed = StoneBedUseEvent.SESSION_TICKS - session.getInt("ticks");
        if (elapsed < DELAY_TICKS) return;

        // флаг снимаем сразу: запуск только один раз за "сон"
        session.putBoolean("script", false);
        data.put(StoneBedUseEvent.SESSION_KEY, session);

        boolean started = start(player);
        dbg("Watch encounter {}", started ? "started" : "skipped (busy, another horror scene or wrong dimension)");
    }

    /** Запуск встречи Watch (логика WatchEncounterEvent / "/watchtest spawn"). */
    static boolean start(ServerPlayer player) {
        if (WatchEncounterEvent.busy(player.getUUID()) || DontTurnAroundEvent.busy(player.getUUID())) return false;
        if (!player.isAlive() || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) return false;
        if (!HorrorDirector.start(player, "watch", 140, true)) return false;

        try {
            register(player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("[StoneBedWatch] could not register the encounter in WatchEncounterEvent", e);
            HorrorDirector.finish(player, "watch", false); // освобождаем сцену, раз встреча не создана
            return false;
        }

        PacketDistributor.sendToPlayer(player, new HorrorNetwork.EffectPayload(WatchEncounterEvent.EFFECT));
        return true;
    }

    /** Прервать встречу. Использует открытый метод WatchEncounterEvent.receive: действие 0 = "закончить". Нет встречи - ничего не делает. */
    public static void cancel(ServerPlayer player) {
        WatchEncounterEvent.receive(player, 0);
    }

    /**
     * Заносит встречу во внутренний список WatchEncounterEvent.ACTIVE (как строка
     * ACTIVE.put(uuid, new Encounter(now + 100)) в его команде spawn), не изменяя сам файл.
     */
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
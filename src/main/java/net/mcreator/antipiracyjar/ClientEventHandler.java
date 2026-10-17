package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@EventBusSubscriber(
        modid = "anti_piracyjar",
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME
)
public class ClientEventHandler {
    private static final int[] DELAYS = {
            1200, // [0] До FakeKickScreen (1 минута)
            400,  // [1] FakeKickScreen -> "Ignore that..."
            1200, // [2] "Ignore that..." -> "I see you."
            100,  // [3] "I see you." -> "At your window."
            100,  // [4] "At your window." -> "Don't get up."
            200   // [5] "Don't get up." -> "Continue playing."
    };

    private static final int SECOND_DAY_STAGE = 2;
    private static final long DAYTIME_LIMIT = 9000L;
    private static final String STATE_FILE = "antipiracyjar_client_state.properties";

    private static boolean wasInWorld = false;
    private static boolean stateLoaded = false;
    private static int enterTicks = 0;

    private static int stage = 0;
    private static long startDay = -1L;
    private static int ticksRemaining = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            wasInWorld = false;
            stateLoaded = false;
            enterTicks = 0;
            return;
        }

        if (!wasInWorld) {
            wasInWorld = true;
            stateLoaded = false;
            enterTicks = 0;
        }

        if (!stateLoaded) {
            if (++enterTicks < 20) {
                return;
            }
            loadState(mc);
            stateLoaded = true;
            return;
        }

        if (stage >= DELAYS.length) {
            return;
        }

        if (stage == SECOND_DAY_STAGE) {
            long dayTime = mc.level.getDayTime();
            long day = dayTime / 24000L;
            long timeOfDay = dayTime % 24000L;

            boolean allowed = day >= startDay + 1L && mc.level.isDay() && timeOfDay < DAYTIME_LIMIT;
            if (!allowed) {
                ticksRemaining = DELAYS[stage];
                return;
            }
        }

        if (--ticksRemaining > 0) {
            return;
        }

        runStage(mc, stage);
        stage++;
        saveState(mc);

        if (stage < DELAYS.length) {
            ticksRemaining = DELAYS[stage];
        }
    }

    private static void runStage(Minecraft mc, int currentStage) {
        switch (currentStage) {
            case 0 -> mc.setScreen(new FakeKickScreen());
            case 1 -> {
                Component message = Component.literal("<???>")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("Ignore that. Feel free to continue.")
                                .withStyle(ChatFormatting.WHITE));
                sendMessage(mc, message);
            }
            case 2 -> sendRedMessage(mc, "I see you.");
            case 3 -> sendRedMessage(mc, "At your window.");
            case 4 -> sendRedMessage(mc, "Don't get up.");
            case 5 -> {
                sendRedMessage(mc, "Continue playing.");
                notifyServerMessagesFinished(mc);
            }
        }
    }

    private static void notifyServerMessagesFinished(Minecraft mc) {
        if (mc.player != null && mc.getConnection() != null) {
            long currentDay = mc.level.getDayTime() / 24000L;
            // Отправляем команду серверу от лица клиента для фиксации дня на сервере
            mc.player.connection.sendCommand("anti_piracy_finish_messages " + currentDay);
        }
    }

    private static void sendRedMessage(Minecraft mc, String text) {
        sendMessage(mc, Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static void sendMessage(Minecraft mc, Component message) {
        if (mc.player == null) {
            return;
        }

        mc.player.sendSystemMessage(message);
        mc.player.playSound(SoundEvents.AMBIENT_CAVE.value(), 1.0F, 1.0F);
    }

    private static Path stateFile(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
        }
        var serverData = mc.getCurrentServer();
        String key = serverData != null ? serverData.ip : "unknown";
        key = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("antipiracyjar").resolve("servers").resolve(key + ".properties");
    }

    private static void loadState(Minecraft mc) {
        stage = 0;
        startDay = -1L;

        Path file = stateFile(mc);
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Properties props = new Properties();
                props.load(in);
                stage = Math.max(0, Math.min(DELAYS.length, Integer.parseInt(props.getProperty("stage", "0"))));
                startDay = Long.parseLong(props.getProperty("startDay", "-1"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (startDay < 0L) {
            startDay = mc.level.getDayTime() / 24000L;
            saveState(mc);
        }

        ticksRemaining = stage < DELAYS.length ? DELAYS[stage] : 0;

        // Если сообщения уже были полностью завершены ранее
        if (stage >= DELAYS.length) {
            notifyServerMessagesFinished(mc);
        }
    }

    private static void saveState(Minecraft mc) {
        try {
            Path file = stateFile(mc);
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("stage", Integer.toString(stage));
            props.setProperty("startDay", Long.toString(startDay));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "antipiracyjar client progress");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
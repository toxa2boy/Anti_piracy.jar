package net.mcreator.antipiracyjar.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = "anti_piracyjar", bus = EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_SHADER;
    public static final ModConfigSpec.BooleanValue LOCK_RESOLUTION;

    public static boolean enableShader = true;
    public static boolean lockResolution = true;

    static {
        BUILDER.push("general");
        ENABLE_SHADER = BUILDER.define("enable_shader", true);
        LOCK_RESOLUTION = BUILDER.define("lock_resolution", true);
        BUILDER.pop();
        SPEC = BUILDER.build();

        createConfigFileIfNotExists();
        readConfigDirectly();
    }

    public static void createConfigFileIfNotExists() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("anti_piracyjar-client.toml");
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                String defaultConfig = "[general]\n" +
                        "enable_shader = true\n" +
                        "lock_resolution = true\n";
                Files.writeString(configPath, defaultConfig);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readConfigDirectly() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("anti_piracyjar-client.toml");
            if (Files.exists(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("enable_shader")) {
                        enableShader = trimmed.contains("true");
                    } else if (trimmed.startsWith("lock_resolution")) {
                        lockResolution = trimmed.contains("true");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            update();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            update();
        }
    }

    private static void update() {
        enableShader = ENABLE_SHADER.get();
        lockResolution = LOCK_RESOLUTION.get();
    }
}
package net.mcreator.antipiracyjar;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public class AntiPiracyWorldHandler {

    private static final String WORLD_NAME = "\u76DC\u7248\u662F\u72AF\u7F6A\u884C\u70BA";
    private static boolean worldCopied = false;
    private static Field summaryField = null;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!worldCopied && event.getScreen() instanceof TitleScreen) {
            copyWorldFromAssets();
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof SelectWorldScreen screen) {
            for (GuiEventListener listener : screen.children()) {
                if (listener instanceof WorldSelectionList worldList) {
                    worldList.children().removeIf(entry -> {
                        if (entry instanceof WorldSelectionList.WorldListEntry worldEntry) {
                            try {
                                if (summaryField == null) {
                                    for (Field f : WorldSelectionList.WorldListEntry.class.getDeclaredFields()) {
                                        if (LevelSummary.class.isAssignableFrom(f.getType())) {
                                            summaryField = f;
                                            summaryField.setAccessible(true);
                                            break;
                                        }
                                    }
                                }
                                if (summaryField != null) {
                                    LevelSummary summary = (LevelSummary) summaryField.get(worldEntry);
                                    if (summary != null) {
                                        return !summary.getLevelId().equals(WORLD_NAME);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        return false;
                    });
                }
            }
        }
    }

    private static void copyWorldFromAssets() {
        if (worldCopied) {
            return;
        }

        try {
            Path targetDir = FMLPaths.GAMEDIR.get().resolve("saves").resolve(WORLD_NAME);
            if (!Files.exists(targetDir)) {
                Optional<? extends ModContainer> modContainer = ModList.get().getModContainerById("anti_piracyjar");
                if (modContainer.isPresent()) {
                    Path sourceDir = modContainer.get().getModInfo().getOwningFile().getFile().findResource("assets", "anti_piracyjar", "world", WORLD_NAME);
                    if (Files.exists(sourceDir)) {
                        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Path relative = sourceDir.relativize(dir);
                                Path target = targetDir.resolve(relative.toString());
                                if (!Files.exists(target)) {
                                    Files.createDirectories(target);
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Path relative = sourceDir.relativize(file);
                                Path target = targetDir.resolve(relative.toString());
                                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                }
            }
            worldCopied = true;
        } catch (Exception ignored) {
        }
    }
}
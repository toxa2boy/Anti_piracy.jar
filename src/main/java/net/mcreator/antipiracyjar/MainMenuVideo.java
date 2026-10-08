package net.mcreator.antipiracyjar;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class MainMenuVideo {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainMenuVideo.class);
    private static boolean introOpened;

    @SubscribeEvent
    public static void onMenuOpen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen) || introOpened) {
            return;
        }

        introOpened = true;
        Minecraft minecraft = Minecraft.getInstance();
        Screen menu = event.getScreen();
        minecraft.execute(() -> {
            if (minecraft.screen == menu) {
                minecraft.setScreen(new IntroScreen(menu));
            } else {
                introOpened = false;
            }
        });
    }

    private static final class IntroScreen extends Screen {
        private final Screen menu;
        private final CompletableFuture<List<Path>> preparation;
        private final ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "intro_video");
        private List<Path> frames;
        private DynamicTexture texture;
        private SimpleSoundInstance sound;
        private int loadedFrame = -1;
        private int textureWidth;
        private int textureHeight;
        private long startedAt = -1;
        private long spacePressedAt = -1;
        private String error;
        private boolean finished;

        private IntroScreen(Screen menu) {
            super(Component.literal("Intro"));
            this.menu = menu;
            this.preparation = IntroVideoSetup.prepare();
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            if (finished) {
                return;
            }
            if (error != null) {
                graphics.drawCenteredString(font, "Could not prepare intro video", width / 2, height / 2 - 12, 0xFF5555);
                graphics.drawCenteredString(font, "Press ENTER or ESC to continue", width / 2, height / 2 + 12, 0xFFFFFF);
                return;
            }
            if (frames == null) {
                if (!preparation.isDone()) {
                    graphics.drawCenteredString(font, "Preparing video...", width / 2, height / 2, 0xFFFFFF);
                    return;
                }
                try {
                    frames = preparation.join();
                } catch (Exception exception) {
                    fail(exception);
                    return;
                }
            }

            long now = System.nanoTime();
            if (startedAt < 0) {
                if (!loadFrame(0)) {
                    return;
                }
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getMusicManager().stopPlaying();
                SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("lostinfog", "startvideo"));
                sound = SimpleSoundInstance.forUI(event, 1.0F, 1.0F);
                minecraft.getSoundManager().play(sound);
                startedAt = System.nanoTime();
                now = startedAt;
            }

            long elapsed = now - startedAt;
            long frameIndex = elapsed * IntroVideoSetup.FPS / 1_000_000_000L;
            if (frameIndex >= frames.size()) {
                finish();
                return;
            }
            if (!loadFrame((int) frameIndex)) {
                return;
            }

            double scale = Math.min((double) width / textureWidth, (double) height / textureHeight);
            int drawWidth = Math.max(1, (int) Math.round(textureWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(textureHeight * scale));
            graphics.blit(textureLocation, (width - drawWidth) / 2, (height - drawHeight) / 2,
                    drawWidth, drawHeight, 0.0F, 0.0F,
                    textureWidth, textureHeight, textureWidth, textureHeight);

            boolean spaceDown = InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_SPACE);
            if (spaceDown) {
                if (spacePressedAt < 0) {
                    spacePressedAt = now;
                }
                if (now - spacePressedAt >= 2_000_000_000L) {
                    finish();
                    return;
                }
            } else {
                spacePressedAt = -1;
            }
            if (elapsed >= 5_000_000_000L || spaceDown) {
                graphics.drawCenteredString(font, "Hold SPACE to skip", width / 2, height - 30, 0xFFFFFF);
            }
        }

        private boolean loadFrame(int index) {
            if (loadedFrame == index && texture != null) {
                return true;
            }
            try (InputStream input = Files.newInputStream(frames.get(index));
                 NativeImage image = NativeImage.read(input)) {
                if (texture == null) {
                    textureWidth = image.getWidth();
                    textureHeight = image.getHeight();
                    texture = new DynamicTexture(textureWidth, textureHeight, false);
                    Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
                }
                texture.getPixels().copyFrom(image);
                texture.upload();
                loadedFrame = index;
                return true;
            } catch (Exception exception) {
                fail(exception);
                return false;
            }
        }

        private void fail(Exception exception) {
            error = exception.toString();
            LOGGER.error("Unable to play AntiPiracyJar intro", exception);
            cleanup();
        }

        private void finish() {
            if (!finished) {
                finished = true;
                Minecraft.getInstance().setScreen(menu);
            }
        }

        private void cleanup() {
            Minecraft minecraft = Minecraft.getInstance();
            if (sound != null) {
                minecraft.getSoundManager().stop(sound);
                sound = null;
            }
            if (texture != null) {
                minecraft.getTextureManager().release(textureLocation);
                texture = null;
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (error != null && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_ESCAPE)) {
                finish();
                return true;
            }
            if (keyCode == InputConstants.KEY_SPACE) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void onClose() {
            if (error != null) {
                finish();
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return error != null;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void removed() {
            cleanup();
            super.removed();
        }
    }
}

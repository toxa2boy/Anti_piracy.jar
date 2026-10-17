package net.mcreator.antipiracyjar;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class FinalVideoScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinalVideoScreen.class);

    public static void open() {
        Minecraft.getInstance().setScreen(new FinalVideoScreen());
    }

    @SubscribeEvent
    public static void preload(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        FinalVideoSetup.prepare();
    }

        private final CompletableFuture<List<Path>> preparation;
        private final ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "final_video");
        private List<Path> frames;
        private DynamicTexture texture;
        private SimpleSoundInstance sound;
        private int loadedFrame = -1;
        private int textureWidth;
        private int textureHeight;
        private long startedAt = -1;

        private boolean finished;
        private boolean failed;

        public FinalVideoScreen() {
            super(Component.literal("Finale"));
            
            preparation = FinalVideoSetup.prepare();
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
            if (failed) {
                graphics.drawCenteredString(font, "Could not prepare final video", width / 2, height / 2 - 12, 0xFF5555);
                graphics.drawCenteredString(font, "Press ENTER to close Minecraft", width / 2, height / 2 + 12, 0xFFFFFF);
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
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "finalvideo"));
                sound = SimpleSoundInstance.forUI(soundEvent, 1.0F, 1.0F);
                minecraft.getSoundManager().play(sound);
                startedAt = System.nanoTime();
                now = startedAt;
            }

            long elapsed = now - startedAt;
            long frameIndex = elapsed * FinalVideoSetup.FPS / 1_000_000_000L;
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
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blit(textureLocation, (width - drawWidth) / 2, (height - drawHeight) / 2,
                    drawWidth, drawHeight, 0.0F, 0.0F,
                    textureWidth, textureHeight, textureWidth, textureHeight);

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
            failed = true;
            LOGGER.error("Unable to play AntiPiracyJar finale", exception);
            cleanup();
        }

        private void finish() {
            if (!finished) {
                finished = true;
                cleanup();
                Minecraft.getInstance().stop();
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
            if (failed && (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_ESCAPE)) {
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
            if (failed) {
                finish();
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return failed;
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

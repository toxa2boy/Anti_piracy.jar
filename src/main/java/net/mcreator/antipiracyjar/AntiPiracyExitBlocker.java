package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(value = Dist.CLIENT)
public class AntiPiracyExitBlocker {

    private static boolean hasTriggered = false;
    private static boolean textCleared = false;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) {
            return;
        }

        List<Button> buttonsToReplace = new ArrayList<>();

        event.getListenersList().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .forEach(button -> {
                    Component message = button.getMessage();
                    if (message != null && isDisconnectButton(message)) {
                        buttonsToReplace.add(button);
                    }
                });

        for (Button oldButton : buttonsToReplace) {
            Component initialMessage = textCleared ? Component.empty() : oldButton.getMessage();
            Button newButton = Button.builder(initialMessage, b -> {
                if (hasTriggered) {
                    return;
                }
                hasTriggered = true;
                textCleared = true;

                b.setMessage(Component.empty());

                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(500);

                        Thread.sleep(3000);

                        Minecraft.getInstance().execute(() -> {
                            if (Minecraft.getInstance().player != null) {
                                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HorrorNetwork.ExitPayload(true));
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            })
            .bounds(oldButton.getX(), oldButton.getY(), oldButton.getWidth(), oldButton.getHeight())
            .build();

            event.removeListener(oldButton);
            event.addListener(newButton);
        }
    }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        hasTriggered = false; textCleared = false;
    }
    private static boolean isDisconnectButton(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key.equals("menu.disconnect") || key.equals("menu.returnToMenu")) {
                return true;
            }
        }
        String text = component.getString().toLowerCase();
        return text.contains("disconnect") || text.contains("quit") || text.contains("выйти");
    }
}
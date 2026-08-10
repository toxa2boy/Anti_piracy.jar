package net.mcreator.antipiracyjar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT)
public class AntiPiracyExitBlocker {

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
            Button newButton = Button.builder(oldButton.getMessage(), b -> {
                b.setMessage(Component.empty());
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("Did you really think that would work?").withStyle(ChatFormatting.RED)
                    );
                }
            })
            .bounds(oldButton.getX(), oldButton.getY(), oldButton.getWidth(), oldButton.getHeight())
            .build();

            event.removeListener(oldButton);
            event.addListener(newButton);
        }
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
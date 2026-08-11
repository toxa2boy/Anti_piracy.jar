
package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;

public class FakeKickScreen extends Screen {
    private int ticksOpened = 0;
    private static final ResourceLocation DIRT_LOCATION = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dirt.png");

    public FakeKickScreen() {
        super(Component.literal("Piracy is a crime!"));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.AMBIENT_CAVE.value(), 1.0F, 0.5F);
        }
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = (this.width - buttonWidth) / 2;
        int y = this.height / 2 + 50;

        this.addRenderableWidget(Button.builder(Component.literal("Quit Game"), button -> {})
                .bounds(x, y, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpened++;
        if (ticksOpened >= 280) {
            this.onClose();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.AMBIENT_CAVE.value(), 1.0F, 0.5F);
            for (int i = 0; i < 20; i++) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("Unknown or incomplete command, see below for error")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int size = 32;
        guiGraphics.setColor(0.25F, 0.25F, 0.25F, 1.0F);
        for (int x = 0; x < this.width; x += size) {
            for (int y = 0; y < this.height; y += size) {
                guiGraphics.blit(DIRT_LOCATION, x, y, 0, 0, size, size, size, size);
            }
        }
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = this.height / 2 - 50;

        guiGraphics.drawCenteredString(this.font, this.title, centerX, startY, 0xFFFFFF);

        Component line1 = Component.literal("The game you possess is an illegal or corrupted copy. Please");
        Component line2 = Component.literal("uninstall the program immediately and purchase the official");
        Component line3 = Component.literal("licensed edition from ");
        Component line3Red = Component.literal("www.minecraft.net").withStyle(ChatFormatting.DARK_RED);

        guiGraphics.drawCenteredString(this.font, line1, centerX, startY + 25, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, line2, centerX, startY + 37, 0xAAAAAA);

        int line3Width = this.font.width(line3) + this.font.width(line3Red);
        int line3X = centerX - line3Width / 2;
        guiGraphics.drawString(this.font, line3, line3X, startY + 49, 0xAAAAAA);
        guiGraphics.drawString(this.font, line3Red, line3X + this.font.width(line3), startY + 49, 0x880000);
    }
}
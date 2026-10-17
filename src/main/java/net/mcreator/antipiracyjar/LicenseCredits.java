package net.mcreator.antipiracyjar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "anti_piracyjar", value = Dist.CLIENT)
public final class LicenseCredits {
    private static final String HELP = "Need help verifying your license?";
    private static final Credit[] CREDITS = {
        new Credit("FLYover67 & Vapsig", "Main developers", "Patreon", "https://www.patreon.com/c/flyover67", "https://discord.gg/BpshSf8jhP"),
        new Credit("HPR", "Models", "CurseForge", "https://www.curseforge.com/members/hpr_777", "https://discord.com/invite/tEz7eUWfGS"),
        new Credit("JDG", "Resource pack", "Modrinth", "https://modrinth.com/user/JDG", "https://discord.com/invite/9dqQeVkmQU")
    };

    private record Credit(String name, String role, String platform, String url, String discord) { }

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen title) {
            event.addListener(new HelpLink(title));
        }
    }

    private static final class HelpLink extends AbstractButton {
        private final Screen parent;

        HelpLink(Screen parent) {
            super(4, 4, Math.min(parent.width - 8, Minecraft.getInstance().font.width(HELP) + 4), 14, Component.literal(HELP));
            this.parent = parent;
        }

        @Override
        public void onPress() { Minecraft.getInstance().setScreen(new CreditsScreen(parent, null)); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            int color = isHoveredOrFocused() ? 0xFFFFA0 : 0xAAAAAA;
            graphics.drawString(font, getMessage(), getX(), getY() + 2, color);
            graphics.fill(getX(), getY() + 12, getX() + Math.min(getWidth(), font.width(getMessage())), getY() + 13, 0xFF000000 | color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class CreditsScreen extends Screen {
        private static final ResourceLocation DIRT = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dirt.png");
        private final Screen parent;
        private final Credit credit;
        private int listTop;

        CreditsScreen(Screen parent, Credit credit) {
            super(Component.literal(credit == null ? "License Verification Support" : credit.name()));
            this.parent = parent;
            this.credit = credit;
        }

        @Override
        protected void init() {
            int buttonWidth = Math.min(260, width - 32);
            int left = (width - buttonWidth) / 2;
            listTop = Math.max(60, height / 2 - 60);
            if (credit == null) {
                for (int i = 0; i < CREDITS.length; i++) {
                    Credit entry = CREDITS[i];
                    addRenderableWidget(Button.builder(Component.literal(entry.name()), button -> minecraft.setScreen(new CreditsScreen(this, entry)))
                            .bounds(left, listTop + i * 48, buttonWidth, 20).build());
                }
            } else {
                int linksTop = Math.max(80, height / 2 - 16);
                addRenderableWidget(Button.builder(Component.literal(credit.platform()), ConfirmLinkScreen.confirmLink(this, credit.url()))
                        .bounds(left, linksTop, buttonWidth, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Discord"), ConfirmLinkScreen.confirmLink(this, credit.discord()))
                        .bounds(left, linksTop + 32, buttonWidth, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                    .bounds(width - 76, 12, 64, 20).build());
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.setColor(0.28F, 0.28F, 0.28F, 1.0F);
            for (int x = 0; x < width; x += 32) {
                for (int y = 0; y < height; y += 32) graphics.blit(DIRT, x, y, 32, 32, 0, 0, 16, 16, 16, 16);
            }
            graphics.setColor(1, 1, 1, 1);
            graphics.fillGradient(0, 0, width, height, 0x70000000, 0xA0000000);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
            graphics.drawCenteredString(font, credit == null ? "The people behind this copy." : credit.role(), width / 2, 42, 0xAAAAAA);
            if (credit == null) {
                for (int i = 0; i < CREDITS.length; i++) graphics.drawCenteredString(font, CREDITS[i].role(), width / 2, listTop + i * 48 + 24, 0xAAAAAA);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() { minecraft.setScreen(parent); }
    }
}
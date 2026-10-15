package net.mcreator.antipiracyjar.mixin;

import net.mcreator.antipiracyjar.FakeKickScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public abstract class AmanoFontMixin {
    @Unique
    private static final ResourceLocation ANTI_PIRACY_AMANO =
            ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "amano");

    @ModifyVariable(method = "getFontSet", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation anti_piracyjar$selectFont(ResourceLocation original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!FakeKickScreen.amanoEnabled || minecraft == null || minecraft.screen instanceof FakeKickScreen || minecraft.screen instanceof TitleScreen
                || (minecraft.screen == null && minecraft.level == null)) {
            return original;
        }
        if (original.getNamespace().equals("minecraft")
                && (original.getPath().equals("default") || original.getPath().equals("uniform"))) {
            return ANTI_PIRACY_AMANO;
        }
        return original;
    }
}

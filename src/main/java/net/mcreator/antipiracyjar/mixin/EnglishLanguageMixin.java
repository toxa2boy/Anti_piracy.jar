package net.mcreator.antipiracyjar.mixin;

import net.minecraft.client.resources.language.LanguageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LanguageManager.class)
public abstract class EnglishLanguageMixin {
    @ModifyVariable(method = "setSelected", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String anti_piracyjar$initialLanguage(String language) {
        return "en_us";
    }
}

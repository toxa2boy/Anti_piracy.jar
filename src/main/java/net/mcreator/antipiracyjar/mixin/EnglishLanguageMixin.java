package net.mcreator.antipiracyjar.mixin;

import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public abstract class EnglishLanguageMixin {
    @Shadow
    public abstract void setSelected(String language);

    @ModifyVariable(method = "setSelected", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String anti_piracyjar$englishOnly(String language) {
        return "en_us";
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void anti_piracyjar$englishBeforeReload(ResourceManager resources, CallbackInfo ci) {
        setSelected("en_us");
    }
}

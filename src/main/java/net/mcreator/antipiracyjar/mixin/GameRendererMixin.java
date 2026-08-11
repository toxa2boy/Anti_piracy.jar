package net.mcreator.antipiracyjar.mixin;

import net.mcreator.antipiracyjar.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow private PostChain postEffect;
    @Shadow public abstract void loadEffect(ResourceLocation resourceLocation);

    @Inject(method = "render", at = @At("HEAD"))
    private void anti_piracyjar$enforceShader(CallbackInfo ci) {
        if (!ClientConfig.enableShader) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && this.postEffect == null) {
            this.loadEffect(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "shaders/post/vhs_horror.json"));
        }
    }

    @Inject(method = "shutdownEffect", at = @At("HEAD"), cancellable = true)
    private void anti_piracyjar$preventShutdown(CallbackInfo ci) {
        if (!ClientConfig.enableShader) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            ci.cancel();
            net.mcreator.antipiracyjar.client.VhsPostProcessingHandler.loaded = false;
        }
    }

    @Inject(method = "loadEffect", at = @At("HEAD"), cancellable = true)
    private void anti_piracyjar$preventLoadOther(ResourceLocation resourceLocation, CallbackInfo ci) {
        if (!ClientConfig.enableShader) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            if (resourceLocation == null || !resourceLocation.equals(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "shaders/post/vhs_horror.json"))) {
                ci.cancel();
                this.loadEffect(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "shaders/post/vhs_horror.json"));
            }
        }
    }

    @Inject(method = "checkEntityPostEffect", at = @At("HEAD"), cancellable = true)
    private void anti_piracyjar$preventVanillaEffect(Entity entity, CallbackInfo ci) {
        if (!ClientConfig.enableShader) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void anti_piracyjar$onResize(int width, int height, CallbackInfo ci) {
        if (!ClientConfig.enableShader) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            this.loadEffect(ResourceLocation.fromNamespaceAndPath("anti_piracyjar", "shaders/post/vhs_horror.json"));
        }
    }
}
package net.mcreator.antipiracyjar.mixin;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
import net.mcreator.antipiracyjar.StoneHuntEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class StoneAccessMixin {
    @Shadow public abstract MinecraftServer getServer();

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void anti_piracyjar$lockedWorld(SocketAddress address, GameProfile profile,
            CallbackInfoReturnable<Component> callback) {
        if (StoneHuntEvent.blocked(getServer(), profile.getId()))
            callback.setReturnValue(Component.literal(StoneHuntEvent.LOCK_MESSAGE));
    }
}

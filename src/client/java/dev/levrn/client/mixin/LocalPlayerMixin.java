package dev.levrn.client.mixin;

import dev.levrn.client.LevrnClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method="sendPosition",at=@At("HEAD"))
    private void levrn$prepare(CallbackInfo ci) { LevrnClient.beforeMovement(); }
    @Inject(method="sendPosition",at=@At("RETURN"))
    private void levrn$finish(CallbackInfo ci) { LevrnClient.afterMovement(); }
}

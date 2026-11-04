package dev.levrn.client.mixin;

import dev.levrn.client.LevrnClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method="isStayingOnGroundSurface",at=@At("RETURN"),cancellable=true)
    private void levrn$edge(CallbackInfoReturnable<Boolean> cir) {
        var player=Minecraft.getInstance().player;
        if((Object)this==player && LevrnClient.gameplayActive() && LevrnClient.STATE.enabled("safe_walk") && player.onGround()) cir.setReturnValue(true);
    }
}

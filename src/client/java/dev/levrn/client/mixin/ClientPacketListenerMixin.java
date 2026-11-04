package dev.levrn.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.levrn.client.LevrnClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @ModifyExpressionValue(method="handleSetEntityMotion",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;getMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 levrn$velocity(Vec3 movement,ClientboundSetEntityMotionPacket packet) {
        var mc=Minecraft.getInstance();var s=LevrnClient.STATE;
        if(!LevrnClient.gameplayActive() || !s.enabled("velocity") || packet.getId()!=mc.player.getId() || mc.player.isPassenger()) return movement;
        LevrnClient.scaledVelocityUpdates++;
        return movement.multiply(s.velocityHorizontal()/100,s.velocityVertical()/100,s.velocityHorizontal()/100);
    }
    @Inject(method="handleMovePlayer",at=@At("TAIL"))
    private void levrn$correction(ClientboundPlayerPositionPacket packet,CallbackInfo ci) { LevrnClient.corrected(); }
}

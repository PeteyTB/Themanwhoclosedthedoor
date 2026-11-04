package dev.levrn.client.mixin;

import dev.levrn.client.LevrnClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ClientCommonPacketListenerImpl.class,priority=2000)
abstract class ClientCommonPacketListenerMixin {
    @Inject(method="send",at=@At("HEAD"))
    private void levrn$packetType(Packet<?> packet,CallbackInfo ci) {LevrnClient.packetSent(packet);}
    @Inject(method="onDisconnect",at=@At("HEAD"))
    private void levrn$disconnectReason(DisconnectionDetails details,CallbackInfo ci) {LevrnClient.disconnectReason(details.reason().getString());}
}

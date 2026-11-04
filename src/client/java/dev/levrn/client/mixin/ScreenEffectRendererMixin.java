package dev.levrn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.levrn.client.LevrnClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
    @Inject(method="renderFire",at=@At("HEAD"),cancellable=true)
    private static void levrn$fire(PoseStack pose,MultiBufferSource buffers,TextureAtlasSprite sprite,CallbackInfo ci) {
        if(LevrnClient.STATE.enabled("no_fire")) ci.cancel();
    }
}

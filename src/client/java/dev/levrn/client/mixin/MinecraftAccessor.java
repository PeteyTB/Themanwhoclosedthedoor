package dev.levrn.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay") int levrn$getUseDelay();
    @Accessor("rightClickDelay") void levrn$setUseDelay(int delay);
}

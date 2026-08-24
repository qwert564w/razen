package org.ryzen.mixin.accessor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({ClientPlayerInteractionManager.class})
public interface MultiPlayerGameModeAccessor {
   @Accessor("blockBreakingCooldown")
   void setDestroyDelay(int var1);
}

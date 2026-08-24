package org.ryzen.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.light.ChunkBlockLightProvider;
import org.ryzen.mixin.accessor.LightEngineAccessor;
import org.ryzen.utils.render.world.DynamicLightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin({ChunkBlockLightProvider.class})
public abstract class BlockLightEngineMixin {
   @ModifyExpressionValue(
      method = {"getLightSourceLuminance"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/block/BlockState;getLuminance()I"
      )}
   )
   private int virtualDynamicLight(int original, long packedPos, BlockState state) {
      return !(((LightEngineAccessor)(Object)this).getChunkSource() instanceof ClientChunkManager)
         ? original
         : Math.max(original, DynamicLightManager.virtualLuminance(packedPos));
   }
}

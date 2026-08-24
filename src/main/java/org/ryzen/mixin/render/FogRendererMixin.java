package org.ryzen.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.ryzen.feature.impl.visual.WorldTweaksFeature;
import org.ryzen.utils.ColorUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({FogRenderer.class})
public abstract class FogRendererMixin {
   @Inject(
      method = {"applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
      )}
   )
   private void applyFogDistances(
      Camera camera,
      int renderDistanceInChunks,
      RenderTickCounter deltaTracker,
      float darkenWorldAmount,
      ClientWorld level,
      CallbackInfoReturnable<Vector4f> cir,
      @Local FogData fog
   ) {
      if (camera.getSubmersionType() == CameraSubmersionType.NONE) {
         WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
         if (worldTweaks != null && worldTweaks.changeFog.getValue()) {
            worldTweaks.applyFog(fog);
         }
      }
   }

   @Inject(
      method = {"getFogColor"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void applyFogColor(
      Camera camera, float partialTick, ClientWorld level, int renderDistanceInChunks, float darkenWorldAmount, CallbackInfoReturnable<Vector4f> cir
   ) {
      if (camera.getSubmersionType() == CameraSubmersionType.NONE) {
         WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
         if (worldTweaks != null && worldTweaks.changeFog.getValue()) {
            int color = worldTweaks.resolvedFogColor();
            Vector4f original = (Vector4f)cir.getReturnValue();
            cir.setReturnValue(
               new Vector4f((float)ColorUtil.red(color) / 255.0F, (float)ColorUtil.green(color) / 255.0F, (float)ColorUtil.blue(color) / 255.0F, original.w)
            );
         }
      }
   }
}

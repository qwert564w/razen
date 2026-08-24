package org.ryzen.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.ryzen.feature.impl.player.FullBrightFeature;
import org.ryzen.feature.impl.visual.WorldTweaksFeature;
import org.ryzen.utils.ColorUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({LightmapTextureManager.class})
public abstract class LightmapRenderStateExtractorMixin {
   @ModifyExpressionValue(
      method = {"update"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"
      )},
      slice = {@Slice(
         from = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;"
         )
      )}
   )
   private Object dynamicFullBrightGamma(Object original) {
      return original instanceof Double gamma ? FullBrightFeature.modifyGamma(gamma) : original;
   }

   @Inject(
      method = {"getDarkness"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void suppressDarkness(LivingEntity entity, float factor, float partialTick, CallbackInfoReturnable<Float> callback) {
      if (FullBrightFeature.shouldSuppressDarkness()) {
         callback.setReturnValue(0.0F);
      }
   }

   @ModifyArg(
      method = {"update"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;"
      ),
      index = 0
   )
   private Vector3fc tintLightmapColor(Vector3fc original) {
      return tint(original);
   }

   @Unique
   private static Vector3f tint(Vector3fc original) {
      WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
      if (worldTweaks != null && worldTweaks.usesWorldColor()) {
         int color = worldTweaks.resolvedWorldColor();
         Vector3f tint = new Vector3f((float)ColorUtil.red(color) / 255.0F, (float)ColorUtil.green(color) / 255.0F, (float)ColorUtil.blue(color) / 255.0F);
         return new Vector3f(original).mul(tint);
      } else {
         return new Vector3f(original);
      }
   }
}

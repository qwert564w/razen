package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({InGameOverlayRenderer.class})
public abstract class ScreenEffectRendererMixin {
   @Inject(
      method = {"renderFireOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderFire(MatrixStack poseStack, VertexConsumerProvider bufferSource, Sprite sprite, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveFireOverlay()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"setFloatingItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDisplayItemActivation(ItemStack itemStack, Random random, CallbackInfo ci) {
      if (itemStack.contains(DataComponentTypes.DEATH_PROTECTION) && RemovalsFeature.shouldRemoveTotemOverlay()) {
         ci.cancel();
      }
   }
}

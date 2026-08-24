package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.ryzen.context.RotationContext;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.visual.SwingAnimationFeature;
import org.ryzen.feature.impl.visual.ViewModelFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({HeldItemRenderer.class})
public abstract class ItemInHandRendererMixin {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"renderFirstPersonItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
         shift = Shift.AFTER
      )}
   )
   private void applyViewModelOffset(
      AbstractClientPlayerEntity player,
      float partialTick,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack stack,
      float equippedProgress,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      int light,
      CallbackInfo ci
   ) {
      ViewModelFeature viewModel = FeatureManager.INSTANCE.getEnabled(ViewModelFeature.class);
      if (viewModel != null && !stack.isEmpty() && (!player.isUsingItem() || player.getActiveHand() != hand)) {
         Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         poseStack.translate(viewModel.offsetX(arm), viewModel.offsetY(arm), viewModel.offsetZ(arm));
      }
   }

   @Inject(
      method = {"swingArm"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void customSwingArm(float swingProgress, MatrixStack poseStack, int direction, Arm arm, CallbackInfo ci) {
      SwingAnimationFeature swing = FeatureManager.INSTANCE.getEnabled(SwingAnimationFeature.class);
      if (swing != null) {
         float progress = MathHelper.clamp(swingProgress, 0.0F, 1.0F);
         float side = direction < 0 ? -1.0F : 1.0F;
         poseStack.translate(side * 0.56F, -0.52F, -0.72F);
         switch (swing.style()) {
            case SLICE: {
               float wave = MathHelper.sin((double)(MathHelper.sqrt(progress) * (float) Math.PI));
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wave * -80.0F));
               poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * wave * -30.0F));
               poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * wave * 10.0F));
               break;
            }
            case SPIRAL:
               float angle = progress * (float) Math.PI * 2.0F;
               poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * MathHelper.sin((double)angle) * 60.0F));
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin((double)(angle * 0.5F)) * -70.0F));
               break;
            case THRUST: {
               float wave = MathHelper.sin((double)(progress * (float) Math.PI));
               float shrink = 1.0F - wave * 0.15F;
               poseStack.translate(0.0F, 0.0F, -wave * 0.4F);
               poseStack.scale(shrink, shrink, 1.0F);
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wave * -15.0F));
               break;
            }
            case SPEAR: {
               float wave = MathHelper.sin((double)(progress * (float) Math.PI));
               poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wave * -95.0F));
               poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * wave * -10.0F));
               poseStack.translate(0.0F, wave * 0.4F, 0.0F);
            }
         }

         ci.cancel();
      }
   }

   @Redirect(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getLerpedPitch(F)F"
      )
   )
   private float useFreeLookPitch(ClientPlayerEntity player, float partialTick) {
      return RotationContext.isActive() ? RotationContext.getFreePitch() : player.getLerpedPitch(partialTick);
   }

   @Redirect(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F"
      )
   )
   private float useCameraPitch(ClientPlayerEntity player, float partialTick) {
      return this.client.gameRenderer.getCamera().getPitch();
   }

   @Redirect(
      method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F"
      )
   )
   private float useCameraYaw(ClientPlayerEntity player, float partialTick) {
      return this.client.gameRenderer.getCamera().getYaw();
   }
}

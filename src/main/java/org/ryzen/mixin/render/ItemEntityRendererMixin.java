package org.ryzen.mixin.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionfc;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.visual.ItemPhysicsFeature;
import org.ryzen.utils.render.ItemEntityRenderStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ItemEntityRenderer.class})
public abstract class ItemEntityRendererMixin {
   private static final float TUMBLE_SPIN_DEGREES = 300.0F;
   private static final float FLAT_ROTATION_DEGREES = 90.0F;
   private static final float GROUND_EPSILON = 0.002F;

   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void captureOnGround(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
      ((ItemEntityRenderStateAccess)state).setOnGround(entity.isOnGround());
   }

   @Redirect(
      method = {"render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"
      )
   )
   private void skipHoverTranslate(
      MatrixStack poseStack,
      float x,
      float y,
      float z,
      ItemEntityRenderState state,
      MatrixStack poseStackArg,
      OrderedRenderCommandQueue collector,
      CameraRenderState cameraState
   ) {
      if (!physicsApplies(state)) {
         poseStack.translate(x, y, z);
      }
   }

   @Redirect(
      method = {"render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"
      )
   )
   private void applyPhysicsTransform(
      MatrixStack poseStack,
      Quaternionfc spinRotation,
      ItemEntityRenderState state,
      MatrixStack poseStackArg,
      OrderedRenderCommandQueue collector,
      CameraRenderState cameraState
   ) {
      if (!physicsApplies(state)) {
         poseStack.multiply(spinRotation);
      } else {
         Box box = state.itemRenderState.getModelBoundingBox();
         float yaw = (float)((state.seed % 360 + 360) % 360);
         if (((ItemEntityRenderStateAccess)state).isOnGround()) {
            poseStack.translate(0.0F, (float)box.maxZ + 0.002F, 0.0F);
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
         } else {
            float centerY = (float)((box.minY + box.maxY) * 0.5);
            float halfHeight = (float)((box.maxY - box.minY) * 0.5);
            float spin = ItemEntity.getRotation(state.age, state.uniqueOffset);
            poseStack.translate(0.0F, halfHeight, 0.0F);
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 300.0F));
            poseStack.translate(0.0F, -centerY, 0.0F);
         }
      }
   }

   @Unique
   private static boolean physicsApplies(ItemEntityRenderState state) {
      return FeatureManager.INSTANCE.getEnabled(ItemPhysicsFeature.class) != null && !state.itemRenderState.isEmpty();
   }
}

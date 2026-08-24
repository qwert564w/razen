package org.ryzen.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;
import org.ryzen.context.RenderContext;
import org.ryzen.event.EventManager;
import org.ryzen.event.Events;
import org.ryzen.event.events.render.FinalGuiRenderEvent;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.combat.NoEntityTraceFeature;
import org.ryzen.feature.impl.visual.RemovalsFeature;
import org.ryzen.feature.impl.visual.ShaderHandsFeature;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.world.ShaderHandsRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   private static ShaderHandsRenderer shaderHandsRenderer;
   @Shadow
   @Final
   private MinecraftClient client;

   @ModifyArg(
      method = {"renderWorld"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
      )
   )
   private Matrix4f captureLevelProjection(Matrix4f projectionMatrix) {
      Render3DUtil.captureLevelProjection(projectionMatrix);
      return projectionMatrix;
   }

   @Inject(
      method = {"tiltViewWhenHurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onBobHurt(MatrixStack poseStack, float partialTick, CallbackInfo ci) {
      if (RemovalsFeature.shouldRemoveShaking()) {
         ci.cancel();
      }
   }

   @WrapOperation(
      method = {"updateCrosshairTarget"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;"
      )}
   )
   private HitResult skipEntityTrace(ClientPlayerEntity localPlayer, float partialTick, Entity cameraEntity, Operation<HitResult> original) {
      HitResult result = (HitResult)original.call(new Object[]{localPlayer, partialTick, cameraEntity});
      return result instanceof EntityHitResult && NoEntityTraceFeature.shouldSkipEntities()
         ? cameraEntity.raycast(localPlayer.getBlockInteractionRange(), partialTick, false)
         : result;
   }

   @WrapOperation(
      method = {"renderHand"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"
      )}
   )
   private void renderShaderHands(
      HeldItemRenderer itemInHandRenderer,
      float partialTick,
      MatrixStack poseStack,
      OrderedRenderCommandQueue collector,
      ClientPlayerEntity player,
      int packedLight,
      Operation<Void> original
   ) {
      ShaderHandsFeature feature = FeatureManager.INSTANCE.getEnabled(ShaderHandsFeature.class);
      if (feature == null) {
         if (shaderHandsRenderer != null) {
            shaderHandsRenderer.release();
            shaderHandsRenderer = null;
         }

         original.call(new Object[]{itemInHandRenderer, partialTick, poseStack, collector, player, packedLight});
      } else {
         if (shaderHandsRenderer == null) {
            shaderHandsRenderer = new ShaderHandsRenderer();
         }

         shaderHandsRenderer.render(feature, () -> original.call(new Object[]{itemInHandRenderer, partialTick, poseStack, collector, player, packedLight}));
      }
   }

   @Inject(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"
      )}
   )
   private void onFinalGuiRender(RenderTickCounter deltaTracker, boolean renderWorld, CallbackInfo ci, @Local DrawContext guiGraphics) {
      if (EventManager.hasListeners(FinalGuiRenderEvent.class)) {
         boolean gameReady = this.client.isFinishedLoading();
         boolean renderHud = gameReady && renderWorld && this.client.world != null;
         boolean renderScreen = gameReady && (this.client.getOverlay() != null || this.client.currentScreen != null);
         int mouseX = (int)this.client.mouse.getScaledX(this.client.getWindow());
         int mouseY = (int)this.client.mouse.getScaledY(this.client.getWindow());
         EventManager.call(
            Events.FINAL_GUI_RENDER.set(this.client, this.client.inGameHud, guiGraphics, deltaTracker, renderHud, renderScreen, mouseX, mouseY)
         );
      }
   }

   @Inject(
      method = {"renderWorld"},
      at = {@At("TAIL")}
   )
   private void onRender3D(RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (EventManager.hasListeners(Render3DEvent.class)) {
         RenderContext.enter3D((GameRenderer)(Object)this, deltaTracker);

         try {
            EventManager.call(Events.RENDER_3D.set(this.client, (GameRenderer)(Object)this, deltaTracker));
         } finally {
            RenderContext.exit3D();
         }
      }
   }
}

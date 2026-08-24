package org.ryzen.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.ryzen.feature.impl.visual.BlockOutlineFeature;
import org.ryzen.feature.impl.visual.ChamsFeature;
import org.ryzen.utils.render.EntityEspStateCache;
import org.ryzen.utils.render.chams.ChamsTargetMatcher;
import org.ryzen.utils.render.world.WorldEffectContext;
import org.ryzen.utils.render.world.WorldEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({WorldRenderer.class})
public abstract class LevelRendererMixin {
   @Shadow
   @Final
   private WorldRenderState worldRenderState;

   @Inject(
      method = {"pushEntityRenders"},
      at = {@At("HEAD")}
   )
   private void captureEntityEspStates(MatrixStack poseStack, WorldRenderState levelRenderState, OrderedRenderCommandQueue submitNodeCollector, CallbackInfo ci) {
      EntityEspStateCache.capture(levelRenderState.entityRenderStates);
      ChamsFeature chams = ChamsFeature.getEnabled();
      if (chams != null && !chams.keepsOriginalModel() && !levelRenderState.entityRenderStates.isEmpty()) {
         List<Entity> targets = ChamsTargetMatcher.collectTargets(MinecraftClient.getInstance(), chams);
         if (!targets.isEmpty()) {
            levelRenderState.entityRenderStates.removeIf(state -> ChamsTargetMatcher.matchingTarget(state, targets) != null);
         }
      }
   }

   @Inject(
      method = {"renderTargetBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void replaceVanillaBlockOutline(
      Immediate bufferSource, MatrixStack poseStack, boolean renderBlockOutline, WorldRenderState levelRenderState, CallbackInfo ci
   ) {
      BlockOutlineFeature feature = BlockOutlineFeature.getEnabled();
      if (feature != null && feature.usesShader()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void renderWorldEffects(
      ObjectAllocator graphicsResourceAllocator,
      RenderTickCounter deltaTracker,
      boolean renderBlockOutline,
      Camera camera,
      Matrix4f frustumMatrix,
      Matrix4f projectionMatrix,
      Matrix4f modelViewMatrix,
      GpuBufferSlice fogParameters,
      Vector4f skyColor,
      boolean hasCapturedFrustum,
      CallbackInfo ci
   ) {
      WorldEffects.render(new WorldEffectContext(this.worldRenderState, this.worldRenderState.cameraRenderState, deltaTracker.getTickProgress(false), skyColor));
   }
}

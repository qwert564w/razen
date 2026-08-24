package org.ryzen.utils.render.chams;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ryzen.feature.impl.visual.PopChamsFeature;
import org.ryzen.utils.ColorUtil;

@Environment(EnvType.CLIENT)
public final class PopChamsRenderer {
   private static final int FULL_BRIGHT = 15728880;
   private static final float MODEL_OFFSET_Y = -1.501F;
   private static final float MODEL_DILATION = -0.2F;
   private static final float SCALE_AMOUNT = 0.7F;
   private static final float RISE_AMOUNT = 0.45F;
   private static final int CLEAR = 0;
   private final PopChamsCompositeEffect composite = new PopChamsCompositeEffect();
   private SimpleFramebuffer maskBuffer;
   private OrderedRenderCommandQueueImpl storage;
   private RenderDispatcher dispatcher;
   private PlayerEntityModel model;

   public void render(WorldRenderState levelRenderState, PopChamsFeature feature) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.world != null && minecraft.player != null && minecraft.gameRenderer != null && levelRenderState != null) {
         long nowNanos = System.nanoTime();
         List<PopChamsFeature.Snapshot> snapshots = feature.activeSnapshots(nowNanos);
         if (!snapshots.isEmpty()) {
            Framebuffer output = minecraft.getFramebuffer();
            if (output != null && output.getColorAttachmentView() != null) {
               this.ensureResources(minecraft, output.textureWidth, output.textureHeight);
               if (this.maskBuffer != null && this.dispatcher != null && this.model != null) {
                  RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.maskBuffer.getColorAttachment(), 0);
                  Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
                  modelViewStack.pushMatrix();
                  modelViewStack.mul(new Matrix4f().rotation(new Quaternionf(levelRenderState.cameraRenderState.orientation).conjugate()));

                  try {
                     this.submitPass(snapshots, levelRenderState, feature, nowNanos, output.getColorAttachmentView(), false);
                     this.submitPass(snapshots, levelRenderState, feature, nowNanos, this.maskBuffer.getColorAttachmentView(), true);
                  } finally {
                     modelViewStack.popMatrix();
                  }

                  this.composite.render(this.maskBuffer, output, feature.effectiveGlowRadius());
               }
            }
         }
      }
   }

   private void submitPass(
      List<PopChamsFeature.Snapshot> snapshots, WorldRenderState levelRenderState, PopChamsFeature feature, long nowNanos, GpuTextureView output, boolean mask
   ) {
      OrderedRenderCommandQueueImpl storage = this.storage;
      MatrixStack poseStack = new MatrixStack();
      double cameraX = levelRenderState.cameraRenderState.pos.getX();
      double cameraY = levelRenderState.cameraRenderState.pos.getY();
      double cameraZ = levelRenderState.cameraRenderState.pos.getZ();
      int submitted = 0;

      for (PopChamsFeature.Snapshot snapshot : snapshots) {
         float animation = snapshot.animation(nowNanos);
         if (!(animation <= 0.0F)) {
            float eased = easeOutQuart(1.0F - animation);
            float scale = 1.0F + eased * 0.7F;
            float rise = eased * 0.45F;
            PlayerEntityRenderState renderState = buildRenderState(snapshot);
            this.model.setAngles(renderState);
            poseStack.push();
            poseStack.translate(snapshot.x() - cameraX, snapshot.y() - cameraY + (double)rise, snapshot.z() - cameraZ);
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - snapshot.bodyYaw()));
            poseStack.scale(-scale, -scale, scale);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            int renderColor = mask ? ColorUtil.withAlpha(snapshot.baseColor(), 255) : ColorUtil.multiplyAlpha(snapshot.baseColor(), animation);
            this.dispatcherSubmit(
               storage,
               renderState,
               poseStack,
               mask
                  ? PopChamsRenderTypes.mask(snapshot.texture(), snapshot.textured())
                  : PopChamsRenderTypes.model(snapshot.texture(), snapshot.textured(), feature.blending.getValue()),
               renderColor
            );
            poseStack.pop();
            submitted++;
         }
      }

      if (submitted != 0) {
         GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
         GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;

         try {
            RenderSystem.outputColorTextureOverride = output;
            RenderSystem.outputDepthTextureOverride = null;
            this.dispatcher.render();
         } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
         }
      }
   }

   private void dispatcherSubmit(
      OrderedRenderCommandQueueImpl storage, PlayerEntityRenderState renderState, MatrixStack poseStack, RenderLayer renderType, int color
   ) {
      storage.submitModel(this.model, renderState, poseStack, renderType, 15728880, OverlayTexture.DEFAULT_UV, color, null, 0, null);
   }

   private static PlayerEntityRenderState buildRenderState(PopChamsFeature.Snapshot snapshot) {
      PlayerEntityRenderState state = new PlayerEntityRenderState();
      state.bodyYaw = snapshot.bodyYaw();
      state.relativeHeadYaw = snapshot.relativeHeadYaw();
      state.pitch = snapshot.pitch();
      state.limbSwingAnimationProgress = snapshot.limbProgress();
      state.limbSwingAmplitude = snapshot.limbSpeed();
      return state;
   }

   private void ensureResources(MinecraftClient minecraft, int width, int height) {
      if (this.dispatcher == null) {
         BufferBuilderStorage buffers = minecraft.getBufferBuilders();
         this.storage = new OrderedRenderCommandQueueImpl();
         this.dispatcher = new RenderDispatcher(
            this.storage,
            minecraft.getBlockRenderManager(),
            buffers.getEntityVertexConsumers(),
            minecraft.getAtlasManager(),
            buffers.getOutlineVertexConsumers(),
            buffers.getEffectVertexConsumers(),
            minecraft.textRenderer
         );
      }

      if (this.model == null) {
         this.model = new PlayerEntityModel(minecraft.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
         this.model.getRootPart().scale(new Vector3f(-0.2F, -0.2F, -0.2F));
      }

      if (this.maskBuffer == null || this.maskBuffer.textureWidth != width || this.maskBuffer.textureHeight != height) {
         if (this.maskBuffer != null) {
            this.maskBuffer.delete();
         }

         this.maskBuffer = new SimpleFramebuffer("blade-popchams-mask", width, height, false);
      }
   }

   private static float easeOutQuart(float value) {
      float inverse = 1.0F - value;
      return 1.0F - inverse * inverse * inverse * inverse;
   }

   public void release() {
      if (this.maskBuffer != null) {
         this.maskBuffer.delete();
         this.maskBuffer = null;
      }

      this.composite.release();
   }
}

package org.ryzen.utils.render.chams;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.ryzen.feature.impl.visual.ChamsFeature;
import org.ryzen.utils.render.EntityEspDispatcherBridge;
import org.ryzen.utils.render.EntityEspStateCache;
import org.ryzen.utils.render.HurtUtil;

@Environment(EnvType.CLIENT)
public final class ChamsMaskRenderer {
   private static final int HURT_BUCKETS = 3;
   private SimpleFramebuffer maskBuffer;
   private OrderedRenderCommandQueueImpl isolatedStorage;
   private RenderDispatcher isolatedDispatcher;

   public void renderGroups(WorldRenderState levelRenderState, ChamsFeature feature, Consumer<ChamsMaskRenderer.MaskFrame> composite) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.world != null && minecraft.player != null && minecraft.gameRenderer != null && levelRenderState != null) {
         List<EntityRenderState> states = EntityEspStateCache.currentStates();
         List<Entity> targets = ChamsTargetMatcher.collectTargets(minecraft, feature);
         if (!states.isEmpty() && !targets.isEmpty()) {
            Framebuffer mainTarget = minecraft.getFramebuffer();
            if (mainTarget != null) {
               this.ensureResources(minecraft, mainTarget.textureWidth, mainTarget.textureHeight);
               if (this.maskBuffer != null
                  && this.isolatedDispatcher != null
                  && minecraft.getEntityRenderDispatcher() instanceof EntityEspDispatcherBridge bridge) {
                  ArrayList var15 = new ArrayList(4);

                  for (int poseStack = 0; poseStack <= 3; poseStack++) {
                     var15.add(null);
                  }

                  for (EntityRenderState state : states) {
                     Entity target = ChamsTargetMatcher.matchingTarget(state, targets);
                     if (target != null) {
                        int bucket = Math.round(HurtUtil.easedFactor(target) * 3.0F);
                        List<EntityRenderState> group = (List<EntityRenderState>)var15.get(bucket);
                        if (group == null) {
                           group = new ArrayList<>(4);
                           var15.set(bucket, group);
                        }

                        group.add(state);
                     }
                  }

                  minecraft.getEntityRenderDispatcher()
                     .configure(minecraft.gameRenderer.getCamera(), (Entity)(minecraft.targetedEntity != null ? minecraft.targetedEntity : minecraft.player));
                  MatrixStack poseStack = new MatrixStack();

                  for (int bucket = 0; bucket <= 3; bucket++) {
                     List<EntityRenderState> group = (List<EntityRenderState>)var15.get(bucket);
                     if (group != null && this.renderMask(levelRenderState, bridge, poseStack, group)) {
                        composite.accept(new ChamsMaskRenderer.MaskFrame(this.maskBuffer, mainTarget, (float)bucket / 3.0F));
                     }
                  }
               }
            }
         }
      }
   }

   private boolean renderMask(WorldRenderState levelRenderState, EntityEspDispatcherBridge bridge, MatrixStack poseStack, List<EntityRenderState> group) {
      RenderSystem.getDevice()
         .createCommandEncoder()
         .clearColorAndDepthTextures(this.maskBuffer.getColorAttachment(), 0, this.maskBuffer.getDepthAttachment(), 1.0);
      OrderedRenderCommandQueueImpl storage = this.isolatedStorage;
      double cameraX = levelRenderState.cameraRenderState.pos.getX();
      double cameraY = levelRenderState.cameraRenderState.pos.getY();
      double cameraZ = levelRenderState.cameraRenderState.pos.getZ();
      GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
      GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

      try {
         RenderSystem.outputColorTextureOverride = this.maskBuffer.getColorAttachmentView();
         RenderSystem.outputDepthTextureOverride = this.maskBuffer.getDepthAttachmentView();
         modelViewStack.pushMatrix();
         modelViewStack.mul(new Matrix4f().rotation(new Quaternionf(levelRenderState.cameraRenderState.orientation).conjugate()));

         for (EntityRenderState state : group) {
            bridge.submitForGlow(state, levelRenderState.cameraRenderState, state.x - cameraX, state.y - cameraY, state.z - cameraZ, poseStack, storage);
         }

         this.isolatedDispatcher.render();
      } finally {
         modelViewStack.popMatrix();
         RenderSystem.outputColorTextureOverride = previousColor;
         RenderSystem.outputDepthTextureOverride = previousDepth;
      }

      return true;
   }

   private void ensureResources(MinecraftClient minecraft, int width, int height) {
      if (this.isolatedDispatcher == null) {
         BufferBuilderStorage buffers = minecraft.getBufferBuilders();
         this.isolatedStorage = new OrderedRenderCommandQueueImpl();
         this.isolatedDispatcher = new RenderDispatcher(
            this.isolatedStorage,
            minecraft.getBlockRenderManager(),
            buffers.getEntityVertexConsumers(),
            minecraft.getAtlasManager(),
            buffers.getOutlineVertexConsumers(),
            buffers.getEffectVertexConsumers(),
            minecraft.textRenderer
         );
      }

      if (this.maskBuffer == null || this.maskBuffer.textureWidth != width || this.maskBuffer.textureHeight != height) {
         if (this.maskBuffer != null) {
            this.maskBuffer.delete();
         }

         this.maskBuffer = new SimpleFramebuffer("blade-chams-mask", width, height, true);
      }
   }

   public void release() {
      if (this.maskBuffer != null) {
         this.maskBuffer.delete();
         this.maskBuffer = null;
      }
   }

   @Environment(EnvType.CLIENT)
   public static record MaskFrame(Framebuffer mask, Framebuffer output, float hurtFactor) {
   }
}

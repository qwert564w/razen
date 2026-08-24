package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.system.MemoryStack;
import org.ryzen.context.MinecraftContext;
import org.ryzen.utils.math.MathUtil;
import org.ryzen.utils.render.post.FullscreenQuad;

@Environment(EnvType.CLIENT)
public final class GuiBackdrop {
   private static final int PASS_COUNT = 5;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec2().putVec2().putFloat().get();
   private static SimpleFramebuffer halfTarget;
   private static SimpleFramebuffer quarterTarget;
   private static GpuBuffer[] passUniforms;
   private static float requestedRadius;
   private static float activeRadius = 16.0F;

   private GuiBackdrop() {
   }

   static GpuTextureView acquireView() {
      MinecraftClient mc = MinecraftContext.mc;
      if (mc != null && RenderSystem.tryGetDevice() != null) {
         Framebuffer mainTarget = mc.getFramebuffer();
         if (mainTarget != null && mainTarget.getColorAttachment() != null && mainTarget.textureWidth > 1 && mainTarget.textureHeight > 1) {
            int halfWidth = Math.max(1, mainTarget.textureWidth / 2);
            int halfHeight = Math.max(1, mainTarget.textureHeight / 2);
            if (halfTarget == null || halfTarget.textureWidth != halfWidth || halfTarget.textureHeight != halfHeight) {
               if (halfTarget != null) {
                  halfTarget.delete();
               }

               if (quarterTarget != null) {
                  quarterTarget.delete();
                  quarterTarget = null;
               }

               halfTarget = new SimpleFramebuffer("blade-gui-backdrop-half", halfWidth, halfHeight, false);
               quarterTarget = new SimpleFramebuffer("blade-gui-backdrop-quarter", Math.max(1, halfWidth / 2), Math.max(1, halfHeight / 2), false);
            }

            return halfTarget.getColorAttachmentView();
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void requestBlurRadius(float radiusPx) {
      requestedRadius = Math.max(requestedRadius, radiusPx);
   }

   public static void captureNow() {
      if (halfTarget != null && quarterTarget != null) {
         MinecraftClient mc = MinecraftContext.mc;
         if (mc != null && RenderSystem.tryGetDevice() != null) {
            Framebuffer mainTarget = mc.getFramebuffer();
            if (mainTarget != null
               && mainTarget.getColorAttachment() != null
               && halfTarget.textureWidth == Math.max(1, mainTarget.textureWidth / 2)
               && halfTarget.textureHeight == Math.max(1, mainTarget.textureHeight / 2)) {
               if (requestedRadius > 0.0F) {
                  activeRadius = requestedRadius;
                  requestedRadius = 0.0F;
               }

               ensureStaticResources();
               float offset = MathUtil.clamp(activeRadius / 8.0F, 0.5F, 8.0F);
               GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
               runPass(
                  0,
                  GuiPipelines.GUI_BLUR_DOWN,
                  mainTarget.getColorAttachmentView(),
                  mainTarget.textureWidth,
                  mainTarget.textureHeight,
                  halfTarget,
                  1.0F,
                  sampler
               );
               if (activeRadius >= 6.0F) {
                  runPass(
                     1,
                     GuiPipelines.GUI_BLUR_DOWN,
                     halfTarget.getColorAttachmentView(),
                     halfTarget.textureWidth,
                     halfTarget.textureHeight,
                     quarterTarget,
                     offset,
                     sampler
                  );
                  runPass(
                     2,
                     GuiPipelines.GUI_BLUR_UP,
                     quarterTarget.getColorAttachmentView(),
                     quarterTarget.textureWidth,
                     quarterTarget.textureHeight,
                     halfTarget,
                     offset,
                     sampler
                  );
                  if (activeRadius >= 24.0F) {
                     runPass(
                        3,
                        GuiPipelines.GUI_BLUR_DOWN,
                        halfTarget.getColorAttachmentView(),
                        halfTarget.textureWidth,
                        halfTarget.textureHeight,
                        quarterTarget,
                        offset,
                        sampler
                     );
                     runPass(
                        4,
                        GuiPipelines.GUI_BLUR_UP,
                        quarterTarget.getColorAttachmentView(),
                        quarterTarget.textureWidth,
                        quarterTarget.textureHeight,
                        halfTarget,
                        offset,
                        sampler
                     );
                  }
               }
            }
         }
      }
   }

   private static void runPass(
      int passIndex,
      RenderPipeline pipeline,
      GpuTextureView input,
      int inputWidth,
      int inputHeight,
      SimpleFramebuffer destination,
      float offset,
      GpuSampler sampler
   ) {
      GpuBuffer uniforms = passUniforms[passIndex];
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putFloat(0.5F / (float)inputWidth)
            .putFloat(0.5F / (float)inputHeight)
            .putFloat(0.0F)
            .putFloat(0.0F)
            .putFloat(offset)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(), data);
      } catch (Throwable var15) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var13) {
               var15.addSuppressed(var13);
            }
         }

         throw var15;
      }

      if (stack != null) {
         stack.close();
      }

      RenderPass pass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Ryzen GUI backdrop blur", destination.getColorAttachmentView(), OptionalInt.empty());

      try {
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("GuiKawaseUniforms", uniforms);
         pass.bindTexture("CurrentInput", input, sampler);
         pass.setVertexBuffer(0, FullscreenQuad.buffer());
         pass.draw(0, FullscreenQuad.vertexCount());
      } catch (Throwable var14) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var12) {
               var14.addSuppressed(var12);
            }
         }

         throw var14;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private static void ensureStaticResources() {
      if (passUniforms == null) {
         passUniforms = new GpuBuffer[5];

         for (int i = 0; i < 5; i++) {
            int index = i;
            passUniforms[i] = RenderSystem.getDevice().createBuffer(() -> "Ryzen GUI backdrop UBO " + index, 136, (long)UNIFORM_SIZE);
         }
      }
   }
}

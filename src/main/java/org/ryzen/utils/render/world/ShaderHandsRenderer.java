package org.ryzen.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.Arm;
import org.lwjgl.system.MemoryStack;
import org.ryzen.feature.impl.visual.ShaderHandsFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.post.FullscreenQuad;
import org.ryzen.utils.render.post.KawaseBlur;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class ShaderHandsRenderer {
   private static final int CLEAR = 0;
   private static final int MAX_BLUR_LEVELS = 4;
   private static final int MASK_UNIFORM_SIZE = new Std140SizeCalculator().putVec2().putVec2().get();
   private static final int FILL_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private static final int GLASS_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putFloat().get();
   private static final int OUTLINE_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putFloat().get();
   private static final int HALO_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putFloat().get();
   private static final int TRAIL_UNIFORM_SIZE = new Std140SizeCalculator().putVec2().putFloat().putFloat().putFloat().putFloat().get();
   private static final int FLAME_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putFloat().get();
   private final GpuBuffer maskUniforms = uniformBuffer("Ryzen Arm Fill Mask UBO", MASK_UNIFORM_SIZE);
   private final GpuBuffer fillUniforms = uniformBuffer("Ryzen Arm Fill UBO", FILL_UNIFORM_SIZE);
   private final GpuBuffer glassUniforms = uniformBuffer("Ryzen Arm Glass UBO", GLASS_UNIFORM_SIZE);
   private final GpuBuffer outlineUniforms = uniformBuffer("Ryzen Arm Outline UBO", OUTLINE_UNIFORM_SIZE);
   private final GpuBuffer haloUniforms = uniformBuffer("Ryzen Arm Halo UBO", HALO_UNIFORM_SIZE);
   private final GpuBuffer trailUniforms = uniformBuffer("Ryzen Arm Flame Accum UBO", TRAIL_UNIFORM_SIZE);
   private final GpuBuffer flameUniforms = uniformBuffer("Ryzen Arm Flame UBO", FLAME_UNIFORM_SIZE);
   private final KawaseBlur blur = new KawaseBlur(
      "blade-arm-blur",
      PostPipelines.HAND_BLUR_DOWN,
      PostPipelines.HAND_BLUR_UP,
      "HandBlurUniforms",
      "HandBlurUniforms",
      KawaseBlur.HANDS_UNIFORM_SIZE,
      (builder, inputWidth, inputHeight, offset, alpha) -> builder.putVec2(1.0F / inputWidth, 1.0F / inputHeight).putFloat(offset).putFloat(alpha)
   );
   private SimpleFramebuffer beforeTarget;
   private SimpleFramebuffer maskRawTarget;
   private SimpleFramebuffer maskTarget;
   private SimpleFramebuffer trailA;
   private SimpleFramebuffer trailB;
   private boolean trailUsesA;
   private boolean flameHistoryActive;

   public void render(ShaderHandsFeature feature, Runnable handDraw) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      Framebuffer mainTarget = minecraft.getFramebuffer();
      if (!validMainTarget(mainTarget)) {
         handDraw.run();
      } else {
         this.ensureTargets(mainTarget.textureWidth, mainTarget.textureHeight);
         if (!this.resourcesReady()) {
            handDraw.run();
         } else {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(
               mainTarget.getColorAttachment(), this.beforeTarget.getColorAttachment(), 0, 0, 0, 0, 0, mainTarget.textureWidth, mainTarget.textureHeight
            );
            encoder.copyTextureToTexture(
               mainTarget.getDepthAttachment(), this.beforeTarget.getDepthAttachment(), 0, 0, 0, 0, 0, mainTarget.textureWidth, mainTarget.textureHeight
            );
            handDraw.run();
            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            this.writeMaskUniforms(feature, mainTarget.textureWidth, mainTarget.textureHeight);
            this.renderMask(mainTarget, sampler);
            if (feature.hasFill()) {
               if (feature.hasGlass()) {
                  GpuTextureView scene = this.beforeTarget.getColorAttachmentView();
                  float blurRadius = feature.glassBlur.getValue().floatValue();
                  if (blurRadius > 0.001F) {
                     scene = this.blurTexture(scene, mainTarget.textureWidth, mainTarget.textureHeight, blurRadius, false, sampler);
                  }

                  this.writeGlassUniforms(feature);
                  this.renderGlass(mainTarget, scene, sampler);
               } else {
                  this.writeFillUniforms(feature);
                  this.renderFill(feature, mainTarget, sampler);
               }
            }

            if (feature.hasOutline()) {
               this.writeOutlineUniforms(feature);
               this.renderOutline(mainTarget, sampler);
            }

            if (feature.hasGlow()) {
               GpuTextureView blurredMask = this.blurTexture(
                  this.maskTarget.getColorAttachmentView(),
                  mainTarget.textureWidth,
                  mainTarget.textureHeight,
                  feature.glowRadius.getValue().floatValue(),
                  true,
                  sampler
               );
               if (feature.hasFlame()) {
                  float time = PostFx.shaderTime() * feature.flameSpeed.getValue().floatValue();
                  this.writeTrailUniforms(feature, mainTarget.textureWidth, mainTarget.textureHeight, time);
                  GpuTextureView flame = this.renderTrail(blurredMask, sampler);
                  this.writeFlameUniforms(feature, time);
                  this.renderFlame(mainTarget, flame, sampler);
                  this.flameHistoryActive = true;
               } else {
                  this.clearFlameHistoryIfNeeded();
                  this.writeHaloUniforms(feature);
                  this.renderHalo(mainTarget, blurredMask, sampler);
               }
            } else {
               this.clearFlameHistoryIfNeeded();
            }
         }
      }
   }

   public void clearHistory() {
      this.trailUsesA = false;
      this.flameHistoryActive = false;
      clear(this.trailA);
      clear(this.trailB);
   }

   public void release() {
      this.beforeTarget = destroy(this.beforeTarget);
      this.maskRawTarget = destroy(this.maskRawTarget);
      this.maskTarget = destroy(this.maskTarget);
      this.trailA = destroy(this.trailA);
      this.trailB = destroy(this.trailB);
      this.blur.release();
      this.maskUniforms.close();
      this.fillUniforms.close();
      this.glassUniforms.close();
      this.outlineUniforms.close();
      this.haloUniforms.close();
      this.trailUniforms.close();
      this.flameUniforms.close();
   }

   private static SimpleFramebuffer destroy(SimpleFramebuffer target) {
      if (target != null) {
         target.delete();
      }

      return null;
   }

   private void clearFlameHistoryIfNeeded() {
      if (this.flameHistoryActive) {
         this.clearHistory();
      }
   }

   private void renderMask(Framebuffer mainTarget, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm Fill mask", this.maskRawTarget);

      try {
         pass.setPipeline(PostPipelines.HAND_MASK);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandMaskUniforms", this.maskUniforms);
         pass.bindTexture("BeforeTexture", this.beforeTarget.getColorAttachmentView(), sampler);
         pass.bindTexture("AfterTexture", mainTarget.getColorAttachmentView(), sampler);
         pass.bindTexture("BeforeDepth", this.beforeTarget.getDepthAttachmentView(), sampler);
         pass.bindTexture("AfterDepth", mainTarget.getDepthAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var9) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var9.addSuppressed(var7);
            }
         }

         throw var9;
      }

      if (pass != null) {
         pass.close();
      }

      pass = renderPass("Ryzen Arm Fill mask smooth", this.maskTarget);

      try {
         pass.setPipeline(PostPipelines.HAND_MASK_SMOOTH);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandMaskUniforms", this.maskUniforms);
         pass.bindTexture("RawMask", this.maskRawTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var6) {
               var8.addSuppressed(var6);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private void renderFill(ShaderHandsFeature feature, Framebuffer mainTarget, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm fill", mainTarget);

      try {
         pass.setPipeline(feature.hasPlasma() ? PostPipelines.HAND_PLASMA : PostPipelines.HAND_FILL);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandFillUniforms", this.fillUniforms);
         pass.bindTexture("MaskSampler", this.maskTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private void renderGlass(Framebuffer mainTarget, GpuTextureView scene, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm glass fill", mainTarget);

      try {
         pass.setPipeline(PostPipelines.HAND_GLASS);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandGlassUniforms", this.glassUniforms);
         pass.bindTexture("SceneSampler", scene, sampler);
         pass.bindTexture("MaskSampler", this.maskTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private void renderOutline(Framebuffer mainTarget, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm outline", mainTarget);

      try {
         pass.setPipeline(PostPipelines.HAND_OUTLINE);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandOutlineUniforms", this.outlineUniforms);
         pass.bindTexture("MaskSampler", this.maskTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var7) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private void renderHalo(Framebuffer mainTarget, GpuTextureView blurredMask, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm halo", mainTarget);

      try {
         pass.setPipeline(PostPipelines.HAND_HALO);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandHaloUniforms", this.haloUniforms);
         pass.bindTexture("BlurredSampler", blurredMask, sampler);
         pass.bindTexture("MaskSampler", this.maskTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private GpuTextureView renderTrail(GpuTextureView injectTexture, GpuSampler sampler) {
      SimpleFramebuffer source = this.trailUsesA ? this.trailA : this.trailB;
      SimpleFramebuffer destination = this.trailUsesA ? this.trailB : this.trailA;
      RenderPass pass = renderPass("Ryzen Arm flame accumulation", destination);

      try {
         pass.setPipeline(PostPipelines.HAND_TRAIL);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandTrailUniforms", this.trailUniforms);
         pass.bindTexture("PrevSampler", source.getColorAttachmentView(), sampler);
         pass.bindTexture("InjectSampler", injectTexture, sampler);
         draw(pass);
      } catch (Throwable var9) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (pass != null) {
         pass.close();
      }

      this.trailUsesA = !this.trailUsesA;
      return destination.getColorAttachmentView();
   }

   private void renderFlame(Framebuffer mainTarget, GpuTextureView flame, GpuSampler sampler) {
      RenderPass pass = renderPass("Ryzen Arm flame composite", mainTarget);

      try {
         pass.setPipeline(PostPipelines.SHADER_HANDS);
         RenderSystem.bindDefaultUniforms(pass);
         pass.setUniform("HandCompositeUniforms", this.flameUniforms);
         pass.bindTexture("BlurredSampler", flame, sampler);
         pass.bindTexture("MaskSampler", this.maskTarget.getColorAttachmentView(), sampler);
         draw(pass);
      } catch (Throwable var8) {
         if (pass != null) {
            try {
               pass.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (pass != null) {
         pass.close();
      }
   }

   private GpuTextureView blurTexture(GpuTextureView source, int width, int height, float radius, boolean boostAlpha, GpuSampler sampler) {
      int levels = Math.clamp((long)((int)Math.ceil((double)(radius / 16.0F))), 1, 4);
      float offset = Math.max(0.5F, radius / 18.0F);
      return this.blur.run(source, width, height, levels, offset, offset, boostAlpha ? 1.12F : 1.0F, boostAlpha ? 1.04F : 1.0F, sampler);
   }

   private void writeMaskUniforms(ShaderHandsFeature feature, int width, int height) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      boolean left = feature.bothHands.getValue();
      boolean right = feature.bothHands.getValue();
      if (!feature.bothHands.getValue()) {
         Arm mainArm = minecraft.player == null ? Arm.RIGHT : minecraft.player.getMainArm();
         left = mainArm == Arm.LEFT;
         right = mainArm == Arm.RIGHT;
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, MASK_UNIFORM_SIZE)
            .putVec2(1.0F / (float)width, 1.0F / (float)height)
            .putVec2(left ? 1.0F : 0.0F, right ? 1.0F : 0.0F)
            .get();
         write(this.maskUniforms, data);
      } catch (Throwable var11) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeFillUniforms(ShaderHandsFeature feature) {
      int color = feature.resolvedColor();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, FILL_UNIFORM_SIZE)
            .putVec4(
               channel(ColorUtil.red(color)), channel(ColorUtil.green(color)), channel(ColorUtil.blue(color)), feature.fillOpacity.getValue().floatValue()
            )
            .putVec4(PostFx.shaderTime() * feature.plasmaSpeed.getValue().floatValue(), 0.0F, 0.0F, 0.0F)
            .get();
         write(this.fillUniforms, data);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeGlassUniforms(ShaderHandsFeature feature) {
      int color = feature.resolvedColor();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, GLASS_UNIFORM_SIZE)
            .putVec4(
               channel(ColorUtil.red(color)), channel(ColorUtil.green(color)), channel(ColorUtil.blue(color)), feature.fillOpacity.getValue().floatValue()
            )
            .putFloat(feature.mirror.getValue() ? 1.0F : 0.0F)
            .get();
         write(this.glassUniforms, data);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeOutlineUniforms(ShaderHandsFeature feature) {
      int color = feature.resolvedColor();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, OUTLINE_UNIFORM_SIZE)
            .putVec4(channel(ColorUtil.red(color)), channel(ColorUtil.green(color)), channel(ColorUtil.blue(color)), 1.0F)
            .putFloat(feature.outlineThickness.getValue().floatValue())
            .get();
         write(this.outlineUniforms, data);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeHaloUniforms(ShaderHandsFeature feature) {
      int color = feature.resolvedColor();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, HALO_UNIFORM_SIZE)
            .putVec4(
               channel(ColorUtil.red(color)), channel(ColorUtil.green(color)), channel(ColorUtil.blue(color)), feature.glowStrength.getValue().floatValue()
            )
            .putFloat(feature.glowRadius.getValue().floatValue() / 12.0F)
            .get();
         write(this.haloUniforms, data);
      } catch (Throwable var7) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeTrailUniforms(ShaderHandsFeature feature, int width, int height, float time) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, TRAIL_UNIFORM_SIZE)
            .putVec2(1.0F / (float)width, 1.0F / (float)height)
            .putFloat(feature.flameTrail.getValue().floatValue())
            .putFloat(feature.flameSpeed.getValue().floatValue())
            .putFloat(time)
            .putFloat(0.0F)
            .get();
         write(this.trailUniforms, data);
      } catch (Throwable var9) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void writeFlameUniforms(ShaderHandsFeature feature, float time) {
      int color = feature.resolvedColor();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, FLAME_UNIFORM_SIZE)
            .putVec4(
               channel(ColorUtil.red(color)), channel(ColorUtil.green(color)), channel(ColorUtil.blue(color)), feature.glowStrength.getValue().floatValue()
            )
            .putFloat(time)
            .get();
         write(this.flameUniforms, data);
      } catch (Throwable var8) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (stack != null) {
         stack.close();
      }
   }

   private void ensureTargets(int width, int height) {
      boolean resetTrail = this.trailA == null
         || this.trailB == null
         || this.trailA.textureWidth != width
         || this.trailA.textureHeight != height
         || this.trailB.textureWidth != width
         || this.trailB.textureHeight != height;
      this.beforeTarget = this.ensureTarget(this.beforeTarget, "blade-arm-before", width, height, true, PostPipelines.EFFECT_FORMAT);
      this.maskRawTarget = this.ensureTarget(this.maskRawTarget, "blade-arm-mask-raw", width, height, false, PostPipelines.MASK_RAW_FORMAT);
      this.maskTarget = this.ensureTarget(this.maskTarget, "blade-arm-mask", width, height, false, PostPipelines.EFFECT_FORMAT);
      this.trailA = this.ensureTarget(this.trailA, "blade-arm-flame-a", width, height, false, PostPipelines.EFFECT_FORMAT);
      this.trailB = this.ensureTarget(this.trailB, "blade-arm-flame-b", width, height, false, PostPipelines.EFFECT_FORMAT);
      if (resetTrail) {
         this.clearHistory();
      }
   }

   private SimpleFramebuffer ensureTarget(SimpleFramebuffer target, String label, int width, int height, boolean useDepth, TextureFormat format) {
      if (target != null && target.textureWidth == width && target.textureHeight == height) {
         return target;
      } else {
         if (target != null) {
            target.delete();
         }

         return new SimpleFramebuffer(label, width, height, useDepth);
      }
   }

   private boolean resourcesReady() {
      return ready(this.beforeTarget)
         && ready(this.maskRawTarget)
         && ready(this.maskTarget)
         && ready(this.trailA)
         && ready(this.trailB)
         && this.beforeTarget.getDepthAttachment() != null
         && this.beforeTarget.getDepthAttachmentView() != null;
   }

   private static boolean ready(SimpleFramebuffer target) {
      return target != null && target.getColorAttachment() != null && target.getColorAttachmentView() != null;
   }

   private static boolean validMainTarget(Framebuffer target) {
      return target != null
         && target.textureWidth > 0
         && target.textureHeight > 0
         && target.getColorAttachment() != null
         && target.getColorAttachmentView() != null
         && target.getDepthAttachment() != null
         && target.getDepthAttachmentView() != null;
   }

   private static RenderPass renderPass(String label, Framebuffer target) {
      return RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, target.getColorAttachmentView(), OptionalInt.empty());
   }

   private static void draw(RenderPass pass) {
      pass.setVertexBuffer(0, FullscreenQuad.buffer());
      pass.draw(0, FullscreenQuad.vertexCount());
   }

   private static void clear(SimpleFramebuffer target) {
      if (target != null && target.getColorAttachment() != null) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorAttachment(), 0);
      }
   }

   private static void write(GpuBuffer buffer, ByteBuffer data) {
      RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
   }

   private static float channel(int value) {
      return (float)value / 255.0F;
   }

   private static GpuBuffer uniformBuffer(String label, int size) {
      return RenderSystem.getDevice().createBuffer(() -> label, 136, (long)size);
   }
}

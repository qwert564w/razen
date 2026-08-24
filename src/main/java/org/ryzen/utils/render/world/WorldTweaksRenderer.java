package org.ryzen.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.state.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
import org.ryzen.feature.impl.visual.WorldTweaksFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.post.FullscreenQuad;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class WorldTweaksRenderer {
   private static final int SKY_UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get();
   private static final int SATURATION_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private final GpuBuffer skyUniforms = uniformBuffer("Ryzen World Sky UBO", SKY_UNIFORM_SIZE);
   private final GpuBuffer saturationUniforms = uniformBuffer("Ryzen World Saturation UBO", SATURATION_UNIFORM_SIZE);
   private SimpleFramebuffer sceneCopy;
   private SimpleFramebuffer skyClouds;

   public void renderSky(WorldTweaksFeature feature, CameraRenderState cameraState) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      Framebuffer target = minecraft.getFramebuffer();
      if (valid(target) && cameraState != null && cameraState.initialized) {
         Matrix4f projection = Render3DUtil.levelProjectionCopy();
         if (projection != null) {
            Matrix4f inverseViewProjection = projection.mul(new Matrix4f().rotation(new Quaternionf(cameraState.orientation).conjugate())).invert();
            this.writeSkyUniforms(feature, inverseViewProjection, target.textureWidth, target.textureHeight);
            this.ensureSkyClouds(target.textureWidth, target.textureHeight);
            String skyEffect = feature.skyEffect.getValue();
            RenderPipeline cloudsPipeline;
            RenderPipeline compositePipeline;
            switch (skyEffect) {
               case "Nebula":
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_NEBULA;
                  compositePipeline = PostPipelines.WORLD_SKY_NEBULA;
                  break;
               case "Plasma":
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_PLASMA;
                  compositePipeline = PostPipelines.WORLD_SKY_PLASMA;
                  break;
               default:
                  cloudsPipeline = PostPipelines.WORLD_SKY_CLOUDS_DEEP_SPACE;
                  compositePipeline = PostPipelines.WORLD_SKY_DEEP_SPACE;
            }

            GpuSampler depthSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            GpuSampler cloudSampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            RenderPass pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Ryzen WorldTweaks sky clouds", this.skyClouds.getColorAttachmentView(), OptionalInt.empty());

            try {
               pass.setPipeline(cloudsPipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("WorldSkyUniforms", this.skyUniforms);
               pass.bindTexture("DepthSampler", target.getDepthAttachmentView(), depthSampler);
               drawFullscreen(pass);
            } catch (Throwable var17) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var15) {
                     var17.addSuppressed(var15);
                  }
               }

               throw var17;
            }

            if (pass != null) {
               pass.close();
            }

            pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Ryzen WorldTweaks sky", target.getColorAttachmentView(), OptionalInt.empty());

            try {
               pass.setPipeline(compositePipeline);
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("WorldSkyUniforms", this.skyUniforms);
               pass.bindTexture("DepthSampler", target.getDepthAttachmentView(), depthSampler);
               pass.bindTexture("CloudSampler", this.skyClouds.getColorAttachmentView(), cloudSampler);
               drawFullscreen(pass);
            } catch (Throwable var16) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var14) {
                     var16.addSuppressed(var14);
                  }
               }

               throw var16;
            }

            if (pass != null) {
               pass.close();
            }
         }
      }
   }

   public void renderSaturation(WorldTweaksFeature feature) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      Framebuffer target = minecraft.getFramebuffer();
      if (valid(target)) {
         this.ensureSceneCopy(target.textureWidth, target.textureHeight);
         RenderSystem.getDevice()
            .createCommandEncoder()
            .copyTextureToTexture(target.getColorAttachment(), this.sceneCopy.getColorAttachment(), 0, 0, 0, 0, 0, target.textureWidth, target.textureHeight);
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer data = Std140Builder.onStack(stack, SATURATION_UNIFORM_SIZE)
               .putVec4(Math.clamp(1.0F + feature.saturationAmount.getValue().floatValue(), 0.0F, 3.0F), 0.0F, 0.0F, 0.0F)
               .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.saturationUniforms.slice(), data);
         } catch (Throwable var10) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var8) {
                  var10.addSuppressed(var8);
               }
            }

            throw var10;
         }

         if (stack != null) {
            stack.close();
         }

         RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Ryzen WorldTweaks saturation", target.getColorAttachmentView(), OptionalInt.empty());

         try {
            pass.setPipeline(PostPipelines.WORLD_SATURATION);
            RenderSystem.bindDefaultUniforms(pass);
            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            pass.setUniform("SaturationUniforms", this.saturationUniforms);
            pass.bindTexture("SceneSampler", this.sceneCopy.getColorAttachmentView(), sampler);
            drawFullscreen(pass);
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
      }
   }

   private void writeSkyUniforms(WorldTweaksFeature feature, Matrix4f inverseViewProjection, int width, int height) {
      int primary = feature.skyColor1.getValue();
      int secondary = feature.skyColor2.getValue();
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, SKY_UNIFORM_SIZE)
            .putMat4f(inverseViewProjection)
            .putVec4((float)ColorUtil.red(primary) / 255.0F, (float)ColorUtil.green(primary) / 255.0F, (float)ColorUtil.blue(primary) / 255.0F, 1.0F)
            .putVec4((float)ColorUtil.red(secondary) / 255.0F, (float)ColorUtil.green(secondary) / 255.0F, (float)ColorUtil.blue(secondary) / 255.0F, 1.0F)
            .putVec4(PostFx.shaderTime(), feature.skyIntensity.getValue().floatValue(), feature.skySpeed.getValue().floatValue(), 0.0F)
            .putVec4(1.0F / (float)width, 1.0F / (float)height, 0.0F, 0.0F)
            .get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.skyUniforms.slice(), data);
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

   private static void drawFullscreen(RenderPass pass) {
      pass.setVertexBuffer(0, FullscreenQuad.buffer());
      pass.draw(0, FullscreenQuad.vertexCount());
   }

   private void ensureSceneCopy(int width, int height) {
      if (this.sceneCopy == null || this.sceneCopy.textureWidth != width || this.sceneCopy.textureHeight != height) {
         if (this.sceneCopy != null) {
            this.sceneCopy.delete();
         }

         this.sceneCopy = new SimpleFramebuffer("blade-world-tweaks-scene", width, height, false);
      }
   }

   private void ensureSkyClouds(int width, int height) {
      int halfWidth = Math.max(1, width / 2);
      int halfHeight = Math.max(1, height / 2);
      if (this.skyClouds == null || this.skyClouds.textureWidth != halfWidth || this.skyClouds.textureHeight != halfHeight) {
         if (this.skyClouds != null) {
            this.skyClouds.delete();
         }

         this.skyClouds = new SimpleFramebuffer("blade-world-tweaks-sky-clouds", halfWidth, halfHeight, false);
      }
   }

   private static boolean valid(Framebuffer target) {
      return target != null
         && target.textureWidth > 0
         && target.textureHeight > 0
         && target.getColorAttachment() != null
         && target.getColorAttachmentView() != null
         && target.getDepthAttachment() != null
         && target.getDepthAttachmentView() != null;
   }

   private static GpuBuffer uniformBuffer(String label, int size) {
      return RenderSystem.getDevice().createBuffer(() -> label, 136, (long)size);
   }

   public void release() {
      if (this.sceneCopy != null) {
         this.sceneCopy.delete();
         this.sceneCopy = null;
      }

      if (this.skyClouds != null) {
         this.skyClouds.delete();
         this.skyClouds = null;
      }

      this.skyUniforms.close();
      this.saturationUniforms.close();
   }
}

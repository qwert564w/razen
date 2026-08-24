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
import net.minecraft.client.render.state.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.ryzen.feature.impl.visual.SkyShaderFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.post.FullscreenQuad;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class SkyShaderRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().putVec4().putVec4().get();
   private final GpuBuffer uniforms = RenderSystem.getDevice().createBuffer(() -> "Ryzen SkyShader UBO", 136, (long)UNIFORM_SIZE);

   public void render(SkyShaderFeature feature, CameraRenderState cameraState, Vector4f vanillaSkyColor) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      Framebuffer target = minecraft.getFramebuffer();
      if (valid(target) && cameraState != null && cameraState.initialized) {
         Matrix4f projection = Render3DUtil.levelProjectionCopy();
         if (projection != null) {
            Matrix4f inverseViewProjection = projection.mul(new Matrix4f().rotation(new Quaternionf(cameraState.orientation).conjugate())).invert();
            this.writeUniforms(feature, inverseViewProjection, vanillaSkyColor);
            GpuSampler depthSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            RenderPass pass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> "Ryzen SkyShader", target.getColorAttachmentView(), OptionalInt.empty());

            try {
               pass.setPipeline(pipelineFor(feature.getMode()));
               RenderSystem.bindDefaultUniforms(pass);
               pass.setUniform("SkyShaderUniforms", this.uniforms);
               pass.bindTexture("DepthSampler", target.getDepthAttachmentView(), depthSampler);
               pass.setVertexBuffer(0, FullscreenQuad.buffer());
               pass.draw(0, FullscreenQuad.vertexCount());
            } catch (Throwable var13) {
               if (pass != null) {
                  try {
                     pass.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }
               }

               throw var13;
            }

            if (pass != null) {
               pass.close();
            }
         }
      }
   }

   private static RenderPipeline pipelineFor(String mode) {
      return switch (mode) {
         case "Summer" -> PostPipelines.SKYSHADER_SUMMER;
         case "Plasma" -> PostPipelines.SKYSHADER_PLASMA;
         case "Pulsar" -> PostPipelines.SKYSHADER_PULSAR;
         case "Sakura" -> PostPipelines.SKYSHADER_SAKURA;
         default -> PostPipelines.SKYSHADER_SPACE;
      };
   }

   private void writeUniforms(SkyShaderFeature feature, Matrix4f inverseViewProjection, Vector4f vanillaSkyColor) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Std140Builder builder = Std140Builder.onStack(stack, UNIFORM_SIZE).putMat4f(inverseViewProjection);
         putColor(builder, feature.primaryColor());
         putColor(builder, feature.secondaryColor());
         putColor(builder, feature.backgroundColor());
         builder.putVec4(
            vanillaSkyColor == null ? 0.5F : vanillaSkyColor.x,
            vanillaSkyColor == null ? 0.7F : vanillaSkyColor.y,
            vanillaSkyColor == null ? 1.0F : vanillaSkyColor.z,
            1.0F
         );
         builder.putVec4(PostFx.shaderTime(), feature.effectiveSpeed(), feature.effectiveIntensity(), feature.effectiveScale());
         builder.putVec4(feature.effectiveOverlay(), feature.isSummerNight() ? 1.0F : 0.0F, 0.0F, 0.0F);
         ByteBuffer data = builder.get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
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

   private static void putColor(Std140Builder builder, int color) {
      builder.putVec4((float)ColorUtil.red(color) / 255.0F, (float)ColorUtil.green(color) / 255.0F, (float)ColorUtil.blue(color) / 255.0F, 1.0F);
   }

   private static boolean valid(Framebuffer target) {
      return target != null
         && target.textureWidth > 0
         && target.textureHeight > 0
         && target.getColorAttachmentView() != null
         && target.getDepthAttachmentView() != null;
   }

   public void release() {
      this.uniforms.close();
   }
}

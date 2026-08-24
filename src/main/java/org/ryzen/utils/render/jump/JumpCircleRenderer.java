package org.ryzen.utils.render.jump;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.Textures;

@Environment(EnvType.CLIENT)
public final class JumpCircleRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.of("ryzen:pipeline/world/jump_circle"))
      .withVertexShader(Identifier.of("ryzen:core/jump_circle"))
      .withFragmentShader(Identifier.of("ryzen:core/jump_circle"))
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withSampler("iChannel0")
      .withUniform("JumpCircleUniforms", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.TRIANGLES)
      .withCull(false)
      .build();

   public void render(Vec3d center, float radius, float alpha, float time, int color, boolean glowEdge) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null && !(radius <= 0.001F) && !(alpha <= 0.003F)) {
         Framebuffer target = mc.getFramebuffer();
         GpuTextureView colorView = target != null ? target.getColorAttachmentView() : null;
         if (colorView != null) {
            AbstractTexture frequencyTexture = mc.getTextureManager().getTexture(Textures.Shader.JUMP_FREQUENCY);
            GpuTextureView frequencyView = frequencyTexture != null ? frequencyTexture.getGlTextureView() : null;
            if (frequencyView != null) {
               GpuDevice device = RenderSystem.getDevice();
               BuiltBuffer meshData = this.buildMesh(mc, center, radius);
               GpuBuffer vertexBuffer = null;
               GpuBuffer uniformBuffer = null;

               try {
                  vertexBuffer = device.createBuffer(() -> "Ryzen Jump Circle Vertices", 40, meshData.getBuffer());
                  uniformBuffer = this.uploadUniform(color, time, alpha, glowEdge);
                  GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                  GpuTextureView depthView = target.getDepthAttachmentView();
                  RenderPass pass = depthView != null
                     ? device.createCommandEncoder()
                        .createRenderPass(() -> "Ryzen Jump Circle Pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
                     : device.createCommandEncoder().createRenderPass(() -> "Ryzen Jump Circle Pass", colorView, OptionalInt.empty());

                  try {
                     pass.setPipeline(PIPELINE);
                     pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                     pass.setUniform("JumpCircleUniforms", uniformBuffer);
                     pass.bindTexture("iChannel0", frequencyView, sampler);
                     pass.setVertexBuffer(0, vertexBuffer);
                     pass.draw(0, 6);
                  } finally {
                     pass.close();
                  }
               } finally {
                  if (uniformBuffer != null) {
                     uniformBuffer.close();
                  }

                  if (vertexBuffer != null) {
                     vertexBuffer.close();
                  }

                  meshData.close();
               }
            }
         }
      }
   }

   private BuiltBuffer buildMesh(MinecraftClient mc, Vec3d center, float radius) {
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getCameraPos();
      Matrix4f pose = Render3DUtil.cameraViewPose(camera);
      Vec3d xAxis = new Vec3d((double)radius, 0.0, 0.0);
      Vec3d zAxis = new Vec3d(0.0, 0.0, (double)radius);
      Vec3d lifted = center.add(0.0, 0.04, 0.0);
      Vector4f p1 = Render3DUtil.toViewSpace(lifted.add(xAxis).add(zAxis), cameraPos, pose);
      Vector4f p2 = Render3DUtil.toViewSpace(lifted.add(xAxis).subtract(zAxis), cameraPos, pose);
      Vector4f p3 = Render3DUtil.toViewSpace(lifted.subtract(xAxis).subtract(zAxis), cameraPos, pose);
      Vector4f p4 = Render3DUtil.toViewSpace(lifted.subtract(xAxis).add(zAxis), cameraPos, pose);
      BufferBuilder builder = new BufferBuilder(
         BufferAllocator.fixedSized(6 * VertexFormats.POSITION_TEXTURE.getVertexSize()), DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE
      );
      this.addVertex(builder, p1, 1.0F, 1.0F);
      this.addVertex(builder, p2, 1.0F, 0.0F);
      this.addVertex(builder, p3, 0.0F, 0.0F);
      this.addVertex(builder, p1, 1.0F, 1.0F);
      this.addVertex(builder, p3, 0.0F, 0.0F);
      this.addVertex(builder, p4, 0.0F, 1.0F);
      return builder.end();
   }

   private void addVertex(BufferBuilder builder, Vector4f point, float u, float v) {
      builder.vertex(point.x, point.y, point.z).texture(u, v);
   }

   private GpuBuffer uploadUniform(int color, float time, float alpha, boolean glowEdge) {
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer buffer = device.createBuffer(() -> "Ryzen Jump Circle UBO", 136, (long)UNIFORM_SIZE);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(
               (float)ColorUtil.red(color) / 255.0F,
               (float)ColorUtil.green(color) / 255.0F,
               (float)ColorUtil.blue(color) / 255.0F,
               (float)ColorUtil.alpha(color) / 255.0F
            )
            .putVec4(time, glowEdge ? 1.0F : 0.0F, 0.0F, alpha)
            .get();
         device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
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

      return buffer;
   }
}

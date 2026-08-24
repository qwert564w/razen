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
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Render3DUtil;

@Environment(EnvType.CLIENT)
public final class JumpGlowRenderer {
   private static final int SEGMENTS = 48;
   private static final int VERTEX_COUNT = 288;
   private static final float GLOW_HEIGHT = 1.0F;
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private static final RenderPipeline PIPELINE = RenderPipeline.builder(new Snippet[0])
      .withLocation(Identifier.of("ryzen:pipeline/world/jump_glow"))
      .withVertexShader(Identifier.of("ryzen:core/jump_glow"))
      .withFragmentShader(Identifier.of("ryzen:core/jump_glow"))
      .withUniform("Projection", UniformType.UNIFORM_BUFFER)
      .withSampler("SceneSampler")
      .withUniform("JumpGlowUniforms", UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.TRIANGLES)
      .withCull(false)
      .build();
   private final SceneSnapshot scene = new SceneSnapshot("blade-jump-glow-scene");

   public void render(Vec3d center, float radius, float ringProgress, int color, float fade, float time) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null && !(fade <= 0.003F) && !(radius <= 0.001F)) {
         Framebuffer target = mc.getFramebuffer();
         GpuTextureView colorView = target != null ? target.getColorAttachmentView() : null;
         if (colorView != null) {
            SimpleFramebuffer sceneCopy = this.scene.capture();
            if (sceneCopy != null) {
               float currentRadius = Math.max(0.001F, radius * ringProgress);
               GpuDevice device = RenderSystem.getDevice();
               BuiltBuffer meshData = this.buildMesh(mc, center, currentRadius);
               GpuBuffer vertexBuffer = null;
               GpuBuffer uniformBuffer = null;

               try {
                  vertexBuffer = device.createBuffer(() -> "Ryzen Jump Glow Vertices", 40, meshData.getBuffer());
                  uniformBuffer = this.uploadUniform(color, fade, ringProgress, time);
                  GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                  GpuTextureView depthView = target.getDepthAttachmentView();
                  RenderPass pass = depthView != null
                     ? device.createCommandEncoder()
                        .createRenderPass(() -> "Ryzen Jump Glow Pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
                     : device.createCommandEncoder().createRenderPass(() -> "Ryzen Jump Glow Pass", colorView, OptionalInt.empty());

                  try {
                     pass.setPipeline(PIPELINE);
                     pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                     pass.setUniform("JumpGlowUniforms", uniformBuffer);
                     pass.bindTexture("SceneSampler", sceneCopy.getColorAttachmentView(), sampler);
                     pass.setVertexBuffer(0, vertexBuffer);
                     pass.draw(0, 288);
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
      Vec3d base = center.add(0.0, 0.02, 0.0);
      BufferBuilder builder = new BufferBuilder(
         BufferAllocator.fixedSized(288 * VertexFormats.POSITION_TEXTURE.getVertexSize()), DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE
      );

      for (int i = 0; i < 48; i++) {
         float a0 = (float)((Math.PI * 2) * (double)i / 48.0);
         float a1 = (float)((Math.PI * 2) * (double)(i + 1) / 48.0);
         float u0 = (float)i / 48.0F;
         float u1 = (float)(i + 1) / 48.0F;
         Vec3d b0 = base.add(Math.cos((double)a0) * (double)radius, 0.0, Math.sin((double)a0) * (double)radius);
         Vec3d b1 = base.add(Math.cos((double)a1) * (double)radius, 0.0, Math.sin((double)a1) * (double)radius);
         Vec3d t0 = b0.add(0.0, 1.0, 0.0);
         Vec3d t1 = b1.add(0.0, 1.0, 0.0);
         Vector4f vb0 = Render3DUtil.toViewSpace(b0, cameraPos, pose);
         Vector4f vb1 = Render3DUtil.toViewSpace(b1, cameraPos, pose);
         Vector4f vt0 = Render3DUtil.toViewSpace(t0, cameraPos, pose);
         Vector4f vt1 = Render3DUtil.toViewSpace(t1, cameraPos, pose);
         this.addVertex(builder, vb0, u0, 0.0F);
         this.addVertex(builder, vb1, u1, 0.0F);
         this.addVertex(builder, vt1, u1, 1.0F);
         this.addVertex(builder, vb0, u0, 0.0F);
         this.addVertex(builder, vt1, u1, 1.0F);
         this.addVertex(builder, vt0, u0, 1.0F);
      }

      return builder.end();
   }

   private void addVertex(BufferBuilder builder, Vector4f point, float u, float v) {
      builder.vertex(point.x, point.y, point.z).texture(u, v);
   }

   private GpuBuffer uploadUniform(int color, float fade, float ringProgress, float time) {
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer buffer = device.createBuffer(() -> "Ryzen Jump Glow UBO", 136, (long)UNIFORM_SIZE);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
            .putVec4(
               (float)ColorUtil.red(color) / 255.0F,
               (float)ColorUtil.green(color) / 255.0F,
               (float)ColorUtil.blue(color) / 255.0F,
               (float)ColorUtil.alpha(color) / 255.0F
            )
            .putVec4(fade, time, 0.0F, ringProgress)
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

   public void release() {
      this.scene.release();
   }
}

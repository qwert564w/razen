package org.ryzen.utils.render.particles;

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
import java.util.List;
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
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.Textures;

@Environment(EnvType.CLIENT)
public final class WorldParticleRenderer {
   private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
   private static final RenderPipeline PIPELINE = buildPipeline("world_particle", BlendFunction.ADDITIVE, true);
   private static final RenderPipeline THROUGH_WALLS_PIPELINE = buildPipeline("world_particle_through_walls", BlendFunction.ADDITIVE, false);
   private static final RenderPipeline ALPHA_PIPELINE = buildPipeline("world_particle_alpha", BlendFunction.TRANSLUCENT, true);
   private static final RenderPipeline ALPHA_THROUGH_WALLS_PIPELINE = buildPipeline("world_particle_alpha_through_walls", BlendFunction.TRANSLUCENT, false);

   private static RenderPipeline buildPipeline(String name, BlendFunction blend, boolean depthTest) {
      return RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.of("ryzen:pipeline/world/" + name))
         .withVertexShader(Identifier.of("ryzen:core/world_particle"))
         .withFragmentShader(Identifier.of("ryzen:core/world_particle"))
         .withUniform("Projection", UniformType.UNIFORM_BUFFER)
         .withSampler("BloomSampler")
         .withUniform("WorldParticleUniforms", UniformType.UNIFORM_BUFFER)
         .withBlend(blend)
         .withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.TRIANGLES)
         .withCull(false)
         .build();
   }

   public void render(List<WorldParticleRenderer.Sprite> sprites, float brightness) {
      this.render(sprites, brightness, false, true);
   }

   public void render(List<WorldParticleRenderer.Sprite> sprites, float brightness, boolean throughWalls) {
      this.render(sprites, brightness, throughWalls, true);
   }

   public void render(List<WorldParticleRenderer.Sprite> sprites, float brightness, boolean throughWalls, boolean additive) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!sprites.isEmpty() && mc.world != null) {
         Framebuffer target = mc.getFramebuffer();
         GpuTextureView colorView = target != null ? target.getColorAttachmentView() : null;
         if (colorView != null) {
            AbstractTexture bloom = mc.getTextureManager().getTexture(Textures.Shader.BLOOM);
            GpuTextureView bloomView = bloom != null ? bloom.getGlTextureView() : null;
            if (bloomView != null) {
               BuiltBuffer meshData = this.buildMesh(mc, sprites);
               if (meshData != null) {
                  GpuDevice device = RenderSystem.getDevice();
                  GpuBuffer vertexBuffer = null;
                  GpuBuffer uniformBuffer = null;

                  try {
                     vertexBuffer = device.createBuffer(() -> "Ryzen World Particles Vertices", 40, meshData.getBuffer());
                     uniformBuffer = this.uploadUniform(brightness);
                     GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
                     GpuTextureView depthView = target.getDepthAttachmentView();
                     RenderPass pass = !throughWalls && depthView != null
                        ? device.createCommandEncoder()
                           .createRenderPass(() -> "Ryzen World Particles Pass", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())
                        : device.createCommandEncoder().createRenderPass(() -> "Ryzen World Particles Pass", colorView, OptionalInt.empty());

                     try {
                        RenderPipeline pipeline = additive
                           ? (throughWalls ? THROUGH_WALLS_PIPELINE : PIPELINE)
                           : (throughWalls ? ALPHA_THROUGH_WALLS_PIPELINE : ALPHA_PIPELINE);
                        pass.setPipeline(pipeline);
                        pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                        pass.setUniform("WorldParticleUniforms", uniformBuffer);
                        pass.bindTexture("BloomSampler", bloomView, sampler);
                        pass.setVertexBuffer(0, vertexBuffer);
                        pass.draw(0, sprites.size() * 6);
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
   }

   private GpuBuffer uploadUniform(float brightness) {
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer buffer = device.createBuffer(() -> "Ryzen World Particles UBO", 136, (long)UNIFORM_SIZE);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE).putVec4(brightness, 0.0F, 0.0F, 0.0F).get();
         device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
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

      return buffer;
   }

   private BuiltBuffer buildMesh(MinecraftClient mc, List<WorldParticleRenderer.Sprite> sprites) {
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getCameraPos();
      Matrix4f pose = Render3DUtil.cameraViewPose(camera);
      BufferBuilder builder = new BufferBuilder(
         BufferAllocator.fixedSized(sprites.size() * 6 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize()),
         DrawMode.TRIANGLES,
         VertexFormats.POSITION_TEXTURE_COLOR
      );

      for (WorldParticleRenderer.Sprite sprite : sprites) {
         Vector4f center = Render3DUtil.toViewSpace(sprite.position(), cameraPos, pose);
         float half = sprite.halfSize();
         int color = sprite.color();
         this.addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
         this.addVertex(builder, center, -half, half, 0.0F, 1.0F, color);
         this.addVertex(builder, center, half, half, 1.0F, 1.0F, color);
         this.addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
         this.addVertex(builder, center, half, half, 1.0F, 1.0F, color);
         this.addVertex(builder, center, half, -half, 1.0F, 0.0F, color);
      }

      return builder.endNullable();
   }

   private void addVertex(BufferBuilder builder, Vector4f center, float dx, float dy, float u, float v, int color) {
      builder.vertex(center.x + dx, center.y + dy, center.z).texture(u, v).color(color);
   }

   @Environment(EnvType.CLIENT)
   public static record Sprite(Vec3d position, float halfSize, int color) {
   }
}

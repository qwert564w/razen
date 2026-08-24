package org.ryzen.utils.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public final class PostFx {
   private PostFx() {
   }

   public static void pass(String label, RenderPipeline pipeline, GpuTextureView target, Consumer<RenderPass> setup) {
      RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, target, OptionalInt.empty());

      try {
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         setup.accept(pass);
         pass.setVertexBuffer(0, FullscreenQuad.buffer());
         pass.draw(0, FullscreenQuad.vertexCount());
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

   public static void pass(String label, RenderPipeline pipeline, Framebuffer target, Consumer<RenderPass> setup) {
      pass(label, pipeline, target.getColorAttachmentView(), setup);
   }

   public static GpuBuffer createUniforms(String label, int size) {
      return RenderSystem.getDevice().createBuffer(() -> label, 136, (long)size);
   }

   public static void writeUniforms(GpuBuffer buffer, int size, Consumer<Std140Builder> writer) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         Std140Builder builder = Std140Builder.onStack(stack, size);
         writer.accept(builder);
         ByteBuffer data = builder.get();
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
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

   public static GpuSampler linearSampler() {
      return RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
   }

   public static GpuSampler nearestSampler() {
      return RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
   }

   public static float shaderTime() {
      return (float)(System.nanoTime() % 1000000000000L) / 1.0E9F;
   }
}

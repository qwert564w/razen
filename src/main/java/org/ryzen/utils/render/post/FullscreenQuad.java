package org.ryzen.utils.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;

@Environment(EnvType.CLIENT)
public final class FullscreenQuad {
   private static GpuBuffer buffer;
   private static int vertexCount;

   private FullscreenQuad() {
   }

   public static GpuBuffer buffer() {
      ensure();
      return buffer;
   }

   public static int vertexCount() {
      ensure();
      return vertexCount;
   }

   private static void ensure() {
      if (buffer == null) {
         BufferBuilder builder = new BufferBuilder(
            BufferAllocator.fixedSized(6 * VertexFormats.POSITION_TEXTURE.getVertexSize()), DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE
         );
         builder.vertex(-1.0F, -1.0F, 0.0F).texture(0.0F, 0.0F);
         builder.vertex(1.0F, -1.0F, 0.0F).texture(1.0F, 0.0F);
         builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F);
         builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F);
         builder.vertex(-1.0F, 1.0F, 0.0F).texture(0.0F, 1.0F);
         builder.vertex(-1.0F, -1.0F, 0.0F).texture(0.0F, 0.0F);
         BuiltBuffer mesh = builder.end();

         try {
            vertexCount = mesh.getDrawParameters().vertexCount();
            buffer = RenderSystem.getDevice().createBuffer(() -> "Ryzen fullscreen quad", 32, mesh.getBuffer());
         } catch (Throwable var5) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (mesh != null) {
            mesh.close();
         }
      }
   }
}

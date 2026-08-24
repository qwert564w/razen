package org.ryzen.utils.render.post;

import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.SimpleFramebuffer;

@Environment(EnvType.CLIENT)
public final class PostTarget {
   private final String label;
   private final TextureFormat format;
   private final boolean useDepth;
   private SimpleFramebuffer target;

   public PostTarget(String label, TextureFormat format, boolean useDepth) {
      this.label = label;
      this.format = format;
      this.useDepth = useDepth;
   }

   public SimpleFramebuffer ensure(int width, int height) {
      if (this.target == null || this.target.textureWidth != width || this.target.textureHeight != height) {
         if (this.target != null) {
            this.target.delete();
         }

         this.target = new SimpleFramebuffer(this.label, Math.max(1, width), Math.max(1, height), this.useDepth);
      }

      return this.target;
   }

   public SimpleFramebuffer get() {
      return this.target;
   }

   public void release() {
      if (this.target != null) {
         this.target.delete();
         this.target = null;
      }
   }
}

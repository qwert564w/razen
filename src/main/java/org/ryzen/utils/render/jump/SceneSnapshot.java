package org.ryzen.utils.render.jump;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

@Environment(EnvType.CLIENT)
final class SceneSnapshot {
   private final String label;
   private SimpleFramebuffer copy;

   SceneSnapshot(String label) {
      this.label = label;
   }

   SimpleFramebuffer capture() {
      Framebuffer mainTarget = MinecraftClient.getInstance().getFramebuffer();
      if (mainTarget != null && mainTarget.getColorAttachment() != null && mainTarget.getColorAttachmentView() != null) {
         if (this.copy == null || this.copy.textureWidth != mainTarget.textureWidth || this.copy.textureHeight != mainTarget.textureHeight) {
            if (this.copy != null) {
               this.copy.delete();
            }

            this.copy = new SimpleFramebuffer(this.label, mainTarget.textureWidth, mainTarget.textureHeight, false);
         }

         if (this.copy.getColorAttachment() != null && this.copy.getColorAttachmentView() != null) {
            RenderSystem.getDevice()
               .createCommandEncoder()
               .copyTextureToTexture(
                  mainTarget.getColorAttachment(), this.copy.getColorAttachment(), 0, 0, 0, 0, 0, mainTarget.textureWidth, mainTarget.textureHeight
               );
            return this.copy;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   void release() {
      if (this.copy != null) {
         this.copy.delete();
         this.copy = null;
      }
   }
}

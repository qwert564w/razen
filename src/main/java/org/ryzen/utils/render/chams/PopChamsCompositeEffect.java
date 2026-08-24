package org.ryzen.utils.render.chams;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import org.ryzen.utils.render.post.KawaseBlur;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;

@Environment(EnvType.CLIENT)
public final class PopChamsCompositeEffect {
   private static final int MAX_MIPS = 3;
   private static final int COMPOSITE_SIZE = new Std140SizeCalculator().putVec4().get();
   private final KawaseBlur blur = new KawaseBlur("blade-popchams");
   private GpuBuffer compositeUniforms;

   public void render(Framebuffer mask, Framebuffer output, int radius) {
      if (mask != null
         && output != null
         && mask.getColorAttachmentView() != null
         && output.getColorAttachmentView() != null
         && output.textureWidth > 0
         && output.textureHeight > 0) {
         if (this.compositeUniforms == null) {
            this.compositeUniforms = PostFx.createUniforms("Ryzen PopChams Composite UBO", COMPOSITE_SIZE);
         }

         float screenScale = Math.max(0.5F, (float)output.textureHeight / 1080.0F);
         float scaledRadius = Math.max(0.05F, (float)radius * screenScale);
         int levels = Math.clamp((long)((int)Math.floor(Math.log((double)scaledRadius) / Math.log(2.0)) + 1), 1, 3);
         float offset = Math.min(2.2F, 1.1F * (float)Math.pow((double)scaledRadius, 0.32F));
         float intensity = Math.min(1.5F, (float)radius * 0.15F + 0.45F);
         GpuSampler sampler = PostFx.linearSampler();
         GpuTextureView blurred = this.blur.run(mask.getColorAttachmentView(), output.textureWidth, output.textureHeight, levels, offset, sampler);
         PostFx.writeUniforms(this.compositeUniforms, COMPOSITE_SIZE, builder -> builder.putVec4(intensity, 0.0F, 0.0F, 0.0F));
         PostFx.pass("Ryzen PopChams Composite", PostPipelines.POPCHAMS_COMPOSITE, output, pass -> {
            pass.setUniform("PopChamsComposite", this.compositeUniforms);
            pass.bindTexture("BlurredSampler", blurred, sampler);
         });
      }
   }

   public void release() {
      this.blur.release();
      if (this.compositeUniforms != null) {
         this.compositeUniforms.close();
         this.compositeUniforms = null;
      }
   }
}

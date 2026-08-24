package org.ryzen.utils.render.chams;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.ryzen.feature.impl.visual.ChamsFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.HurtUtil;
import org.ryzen.utils.render.post.KawaseBlur;
import org.ryzen.utils.render.post.PostFx;
import org.ryzen.utils.render.post.PostPipelines;
import org.ryzen.utils.render.post.PostTarget;

@Environment(EnvType.CLIENT)
public final class ChamsCompositeEffect {
   private static final int MAX_MIPS = 3;
   private static final int STYLE_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
   private final GpuBuffer styleUniforms = PostFx.createUniforms("Ryzen Chams Style UBO", STYLE_SIZE);
   private final KawaseBlur maskBlur = new KawaseBlur("blade-chams-mask");
   private final KawaseBlur sceneBlur = new KawaseBlur("blade-chams-scene");
   private final PostTarget sceneCopy = new PostTarget("blade-chams-scene-copy", PostPipelines.EFFECT_FORMAT, false);

   public void render(ChamsMaskRenderer.MaskFrame frame, ChamsFeature feature) {
      Framebuffer mask = frame.mask();
      Framebuffer output = frame.output();
      if (mask != null && output != null && mask.getColorAttachmentView() != null && output.getColorAttachmentView() != null) {
         int width = output.textureWidth;
         int height = output.textureHeight;
         this.writeStyle(feature, frame.hurtFactor());
         GpuSampler sampler = PostFx.linearSampler();
         GpuTextureView blurredMask = null;
         if (feature.usesInternal() || feature.hasGlow()) {
            float radius = Math.max(1.0F, feature.glowRadius.getValue().floatValue() * 2.0F);
            blurredMask = this.maskBlur
               .run(
                  mask.getColorAttachmentView(),
                  width,
                  height,
                  Math.clamp((long)((int)Math.ceil((double)(radius / 2.0F))), 1, 3),
                  Math.clamp(radius, 0.5F, 4.0F),
                  sampler
               );
         }

         if (feature.hasGlass()) {
            SimpleFramebuffer scene = this.sceneCopy.ensure(width, height);
            RenderSystem.getDevice()
               .createCommandEncoder()
               .copyTextureToTexture(output.getColorAttachment(), scene.getColorAttachment(), 0, 0, 0, 0, 0, width, height);
            GpuTextureView sceneView = scene.getColorAttachmentView();
            if (feature.glassBlur.getValue() > 0.001) {
               float radius = feature.glassBlur.getValue().floatValue() / 8.0F;
               sceneView = this.sceneBlur
                  .run(sceneView, width, height, Math.clamp((long)((int)Math.ceil((double)(radius / 2.0F))), 1, 3), Math.clamp(radius, 0.5F, 4.0F), sampler);
            }

            GpuTextureView finalScene = sceneView;
            this.stylePass("Ryzen Chams Glass", PostPipelines.CHAMS_GLASS, output, pass -> {
               pass.bindTexture("SceneSampler", finalScene, sampler);
               pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler);
            });
         }

         if (feature.hasSolid()) {
            this.stylePass(
               "Ryzen Chams Solid", PostPipelines.CHAMS_SOLID, output, pass -> pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler)
            );
         }

         if (feature.hasShaderFill()) {
            this.stylePass(
               "Ryzen Chams Shader Fill",
               feature.shader.is("Nebula") ? PostPipelines.CHAMS_NEBULA : PostPipelines.CHAMS_PLASMA,
               output,
               pass -> pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler)
            );
         }

         if (feature.usesInternal() && blurredMask != null) {
            GpuTextureView finalBlur = blurredMask;
            this.stylePass("Ryzen Chams Internal", PostPipelines.CHAMS_INTERNAL, output, pass -> {
               pass.bindTexture("BlurredSampler", finalBlur, sampler);
               pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler);
            });
         }

         if (feature.hasOutline()) {
            this.stylePass(
               "Ryzen Chams Outline", PostPipelines.CHAMS_OUTLINE, output, pass -> pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler)
            );
         }

         if (feature.hasGlow() && blurredMask != null) {
            GpuTextureView finalBlur = blurredMask;
            this.stylePass(
               "Ryzen Chams Glow", feature.additiveBlending.getValue() ? PostPipelines.CHAMS_GLOW_ADDITIVE : PostPipelines.CHAMS_GLOW, output, pass -> {
                  pass.bindTexture("BlurredSampler", finalBlur, sampler);
                  pass.bindTexture("MaskSampler", mask.getColorAttachmentView(), sampler);
               }
            );
         }
      }
   }

   private void stylePass(String label, RenderPipeline pipeline, Framebuffer output, Consumer<RenderPass> setup) {
      PostFx.pass(label, pipeline, output, pass -> {
         pass.setUniform("ChamsStyle", this.styleUniforms);
         setup.accept(pass);
      });
   }

   private void writeStyle(ChamsFeature feature, float hurtFactor) {
      int color = HurtUtil.blend(feature.resolvedColor(), hurtFactor, 1.0F);
      PostFx.writeUniforms(
         this.styleUniforms,
         STYLE_SIZE,
         builder -> builder.putVec4(
                  (float)ColorUtil.red(color) / 255.0F,
                  (float)ColorUtil.green(color) / 255.0F,
                  (float)ColorUtil.blue(color) / 255.0F,
                  feature.opacity.getValue().floatValue()
               )
               .putVec4(
                  PostFx.shaderTime() * feature.shaderSpeed.getValue().floatValue(),
                  feature.mirror.getValue() ? 1.0F : 0.0F,
                  feature.outlineThickness.getValue().floatValue(),
                  feature.glowStrength.getValue().floatValue()
               )
      );
   }

   public void release() {
      this.maskBlur.release();
      this.sceneBlur.release();
      this.sceneCopy.release();
      this.styleUniforms.close();
   }
}

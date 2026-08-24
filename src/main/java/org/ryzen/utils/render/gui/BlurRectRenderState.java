package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fc;
import org.ryzen.utils.math.MathUtil;

@Environment(EnvType.CLIENT)
public final class BlurRectRenderState extends UiElementRenderState {
   private final float x0;
   private final float y0;
   private final float x1;
   private final float y1;
   private final int tintColor;
   private final float radius;
   private final float opacity;
   private final TextureSetup textureSetup;

   BlurRectRenderState(
      Matrix3x2fc pose,
      float x0,
      float y0,
      float x1,
      float y1,
      int tintColor,
      float radius,
      float blurRadiusPx,
      float opacity,
      GpuTextureView backdropView,
      ScreenRect scissor
   ) {
      super(pose, scissor, x0, y0, x1 - x0, y1 - y0);
      this.x0 = x0;
      this.y0 = y0;
      this.x1 = x1;
      this.y1 = y1;
      this.tintColor = tintColor;
      this.radius = radius;
      this.opacity = opacity;
      this.textureSetup = TextureSetup.of(backdropView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
      GuiBackdrop.requestBlurRadius(blurRadiusPx);
   }

   public void setupVertices(VertexConsumer vertices) {
      float halfWidth = (this.x1 - this.x0) * 0.5F;
      float halfHeight = (this.y1 - this.y0) * 0.5F;
      int packedSizeX = UiVertexPacking.packSize(halfWidth * 2.0F);
      int packedSizeY = UiVertexPacking.packSize(halfHeight * 2.0F);
      int packedRadius = UiVertexPacking.packRadius(this.radius);
      int packedOpacity = MathUtil.clampByte(Math.round(this.opacity * 255.0F));
      this.addVertex(vertices, this.x0, this.y0, -halfWidth, -halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
      this.addVertex(vertices, this.x0, this.y1, -halfWidth, halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
      this.addVertex(vertices, this.x1, this.y1, halfWidth, halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
      this.addVertex(vertices, this.x1, this.y0, halfWidth, -halfHeight, packedSizeX, packedSizeY, packedRadius, packedOpacity);
   }

   private void addVertex(
      VertexConsumer vertexConsumer, float x, float y, float localX, float localY, int packedSizeX, int packedSizeY, int packedRadius, int packedOpacity
   ) {
      vertexConsumer.vertex(this.transformX(x, y), this.transformY(x, y), 0.0F)
         .color(this.tintColor)
         .texture(localX, localY)
         .overlay(packedSizeX, packedSizeY)
         .light(packedRadius, packedOpacity)
         .normal(0.0F, 0.0F, 1.0F)
         .lineWidth(0.0F);
   }

   public RenderPipeline pipeline() {
      return GuiPipelines.BLUR_RECT;
   }

   public TextureSetup textureSetup() {
      return this.textureSetup;
   }
}

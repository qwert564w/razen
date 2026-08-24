package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fc;

@Environment(EnvType.CLIENT)
public final class MenuBackgroundRenderState extends UiElementRenderState {
   private final float x0;
   private final float y0;
   private final float x1;
   private final float y1;
   private final float seconds;
   private final int mode;
   private final float radius;
   private final int primaryColor;
   private final int secondaryColor;

   public MenuBackgroundRenderState(
      Matrix3x2fc pose, float x0, float y0, float x1, float y1, float seconds, int mode, float radius, int primaryColor, int secondaryColor, ScreenRect scissor
   ) {
      super(pose, scissor, x0, y0, x1 - x0, y1 - y0);
      this.x0 = x0;
      this.y0 = y0;
      this.x1 = x1;
      this.y1 = y1;
      this.seconds = seconds;
      this.mode = mode;
      this.radius = radius;
      this.primaryColor = primaryColor;
      this.secondaryColor = secondaryColor;
   }

   public void setupVertices(VertexConsumer vertices) {
      int packedRg = UiVertexPacking.packU8Pair(this.secondaryColor >> 16 & 0xFF, this.secondaryColor >> 8 & 0xFF);
      int packedBa = UiVertexPacking.packU8Pair(this.secondaryColor & 0xFF, this.secondaryColor >>> 24 & 0xFF);
      float width = Math.max(1.0F, this.x1 - this.x0);
      float height = Math.max(1.0F, this.y1 - this.y0);
      int packedAspect = UiVertexPacking.packRadius(width / height);
      float radiusNorm = Math.min(0.5F, this.radius / height);
      this.addVertex(vertices, this.x0, this.y0, 0.0F, 0.0F, packedRg, packedBa, packedAspect, radiusNorm);
      this.addVertex(vertices, this.x0, this.y1, 0.0F, 1.0F, packedRg, packedBa, packedAspect, radiusNorm);
      this.addVertex(vertices, this.x1, this.y1, 1.0F, 1.0F, packedRg, packedBa, packedAspect, radiusNorm);
      this.addVertex(vertices, this.x1, this.y0, 1.0F, 0.0F, packedRg, packedBa, packedAspect, radiusNorm);
   }

   private void addVertex(VertexConsumer vertexConsumer, float x, float y, float u, float v, int packedRg, int packedBa, int packedAspect, float radiusNorm) {
      vertexConsumer.vertex(this.transformX(x, y), this.transformY(x, y), this.seconds)
         .color(this.primaryColor)
         .texture(u, v)
         .overlay(packedRg, packedBa)
         .light(this.mode, packedAspect)
         .normal(0.0F, 0.0F, 1.0F)
         .lineWidth(radiusNorm);
   }

   public RenderPipeline pipeline() {
      return GuiPipelines.MENU_BACKGROUND;
   }

   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }
}

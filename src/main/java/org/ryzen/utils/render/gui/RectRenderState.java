package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2fc;
import org.ryzen.utils.math.MathUtil;

@Environment(EnvType.CLIENT)
public final class RectRenderState extends UiElementRenderState {
   private final float x0;
   private final float y0;
   private final float x1;
   private final float y1;
   private final int topLeftColor;
   private final int bottomLeftColor;
   private final int bottomRightColor;
   private final int topRightColor;
   private final float topLeftRadius;
   private final float topRightRadius;
   private final float bottomRightRadius;
   private final float bottomLeftRadius;
   private final float borderThickness;
   private final int borderColor;
   private final float shadowBlur;
   private final int shadowColor;
   private final boolean shadow;
   private boolean glassShadow;

   public RectRenderState(
      Matrix3x2fc pose,
      float x0,
      float y0,
      float x1,
      float y1,
      int topLeftColor,
      int bottomLeftColor,
      int bottomRightColor,
      int topRightColor,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float borderThickness,
      int borderColor,
      float shadowBlur,
      int shadowColor,
      boolean shadow,
      ScreenRect scissor
   ) {
      super(
         pose,
         scissor,
         shadow ? x0 - spread(shadowBlur) : x0,
         shadow ? y0 - spread(shadowBlur) : y0,
         x1 - x0 + (shadow ? spread(shadowBlur) * 2.0F : 0.0F),
         y1 - y0 + (shadow ? spread(shadowBlur) * 2.0F : 0.0F)
      );
      this.x0 = x0;
      this.y0 = y0;
      this.x1 = x1;
      this.y1 = y1;
      this.topLeftColor = topLeftColor;
      this.bottomLeftColor = bottomLeftColor;
      this.bottomRightColor = bottomRightColor;
      this.topRightColor = topRightColor;
      this.topLeftRadius = clampRadius(topLeftRadius);
      this.topRightRadius = clampRadius(topRightRadius);
      this.bottomRightRadius = clampRadius(bottomRightRadius);
      this.bottomLeftRadius = clampRadius(bottomLeftRadius);
      this.borderThickness = borderThickness;
      this.borderColor = borderColor;
      this.shadowBlur = shadowBlur;
      this.shadowColor = shadowColor;
      this.shadow = shadow;
   }

   public RectRenderState(
      Matrix3x2fc pose, float x0, float y0, float x1, float y1, int color, float radius, float borderThickness, int borderColor, ScreenRect scissor
   ) {
      this(pose, x0, y0, x1, y1, color, color, color, color, radius, radius, radius, radius, borderThickness, borderColor, 0.0F, 0, false, scissor);
   }

   public RectRenderState(
      Matrix3x2fc pose,
      float x0,
      float y0,
      float x1,
      float y1,
      int topLeftColor,
      int bottomLeftColor,
      int bottomRightColor,
      int topRightColor,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float borderThickness,
      int borderColor,
      ScreenRect scissor
   ) {
      this(
         pose,
         x0,
         y0,
         x1,
         y1,
         topLeftColor,
         bottomLeftColor,
         bottomRightColor,
         topRightColor,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
         borderThickness,
         borderColor,
         0.0F,
         0,
         false,
         scissor
      );
   }

   public static RectRenderState glassShadow(
      Matrix3x2fc pose,
      float x0,
      float y0,
      float x1,
      float y1,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float shadowBlur,
      int shadowColor,
      ScreenRect scissor
   ) {
      RectRenderState state = shadow(pose, x0, y0, x1, y1, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius, shadowBlur, shadowColor, scissor);
      state.glassShadow = true;
      return state;
   }

   public static RectRenderState shadow(
      Matrix3x2fc pose,
      float x0,
      float y0,
      float x1,
      float y1,
      float topLeftRadius,
      float topRightRadius,
      float bottomRightRadius,
      float bottomLeftRadius,
      float shadowBlur,
      int shadowColor,
      ScreenRect scissor
   ) {
      return new RectRenderState(
         pose,
         x0,
         y0,
         x1,
         y1,
         0,
         0,
         0,
         0,
         topLeftRadius,
         topRightRadius,
         bottomRightRadius,
         bottomLeftRadius,
         0.0F,
         0,
         Math.max(shadowBlur, 1.0F),
         shadowColor,
         true,
         scissor
      );
   }

   public void setupVertices(VertexConsumer vertices) {
      float halfWidth = (this.x1 - this.x0) * 0.5F;
      float halfHeight = (this.y1 - this.y0) * 0.5F;
      float spread = this.shadow ? spread(this.shadowBlur) : 0.0F;
      int effectColor = this.shadow ? this.shadowColor : this.borderColor;
      float packedZ = UiVertexPacking.packZ(this.shadow ? this.shadowBlur : this.borderThickness, effectColor >>> 24 & 0xFF, this.shadow);
      int packedSizeX = UiVertexPacking.packSize(halfWidth * 2.0F);
      int packedSizeY = UiVertexPacking.packSize(halfHeight * 2.0F);
      int packedTopLeft = UiVertexPacking.packRadius(this.topLeftRadius);
      int packedTopRight = UiVertexPacking.packRadius(this.topRightRadius);
      float packedBottom = UiVertexPacking.packDual12(this.bottomRightRadius, this.bottomLeftRadius);
      float effectRed = UiVertexPacking.snormChannel(effectColor >> 16 & 0xFF);
      float effectGreen = UiVertexPacking.snormChannel(effectColor >> 8 & 0xFF);
      float effectBlue = UiVertexPacking.snormChannel(effectColor & 0xFF);
      float localX = halfWidth + spread;
      float localY = halfHeight + spread;
      this.addVertex(
         vertices,
         this.x0 - spread,
         this.y0 - spread,
         -localX,
         -localY,
         packedZ,
         packedSizeX,
         packedSizeY,
         packedTopLeft,
         packedTopRight,
         packedBottom,
         effectRed,
         effectGreen,
         effectBlue,
         this.topLeftColor
      );
      this.addVertex(
         vertices,
         this.x0 - spread,
         this.y1 + spread,
         -localX,
         localY,
         packedZ,
         packedSizeX,
         packedSizeY,
         packedTopLeft,
         packedTopRight,
         packedBottom,
         effectRed,
         effectGreen,
         effectBlue,
         this.bottomLeftColor
      );
      this.addVertex(
         vertices,
         this.x1 + spread,
         this.y1 + spread,
         localX,
         localY,
         packedZ,
         packedSizeX,
         packedSizeY,
         packedTopLeft,
         packedTopRight,
         packedBottom,
         effectRed,
         effectGreen,
         effectBlue,
         this.bottomRightColor
      );
      this.addVertex(
         vertices,
         this.x1 + spread,
         this.y0 - spread,
         localX,
         -localY,
         packedZ,
         packedSizeX,
         packedSizeY,
         packedTopLeft,
         packedTopRight,
         packedBottom,
         effectRed,
         effectGreen,
         effectBlue,
         this.topRightColor
      );
   }

   private void addVertex(
      VertexConsumer vertexConsumer,
      float x,
      float y,
      float localX,
      float localY,
      float packedZ,
      int packedSizeX,
      int packedSizeY,
      int packedTopLeft,
      int packedTopRight,
      float packedBottom,
      float effectRed,
      float effectGreen,
      float effectBlue,
      int color
   ) {
      vertexConsumer.vertex(this.transformX(x, y), this.transformY(x, y), packedZ)
         .color(color)
         .texture(localX, localY)
         .overlay(packedSizeX, packedSizeY)
         .light(packedTopLeft, packedTopRight)
         .normal(effectRed, effectGreen, effectBlue)
         .lineWidth(packedBottom);
   }

   private static float clampRadius(float radius) {
      return MathUtil.clamp(radius, 0.0F, 255.9375F);
   }

   private static float spread(float blur) {
      return Math.max(blur, 1.0F);
   }

   public RenderPipeline pipeline() {
      return this.glassShadow ? GuiPipelines.GLASS_SHADOW : GuiPipelines.RECT;
   }

   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }
}

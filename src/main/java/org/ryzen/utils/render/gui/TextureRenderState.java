package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fc;
import org.ryzen.context.MinecraftContext;

@Environment(EnvType.CLIENT)
public final class TextureRenderState extends UiElementRenderState {
   private final GuiTexture texture;
   private final Identifier managedTextureId;
   private final float x;
   private final float y;
   private final float width;
   private final float height;
   private final float u0;
   private final float v0;
   private final float u1;
   private final float v1;
   private final int color;
   private final float cornerRadius;
   private final float deviceScale;
   private final boolean smooth;

   public TextureRenderState(
      Matrix3x2fc pose,
      Identifier textureId,
      boolean managed,
      float x,
      float y,
      float width,
      float height,
      float u0,
      float v0,
      float u1,
      float v1,
      int color,
      float cornerRadius,
      ScreenRect scissor
   ) {
      this(pose, textureId, managed, x, y, width, height, u0, v0, u1, v1, color, cornerRadius, false, scissor);
   }

   public TextureRenderState(
      Matrix3x2fc pose,
      Identifier textureId,
      boolean managed,
      float x,
      float y,
      float width,
      float height,
      float u0,
      float v0,
      float u1,
      float v1,
      int color,
      float cornerRadius,
      boolean smooth,
      ScreenRect scissor
   ) {
      super(pose, scissor, x, y, width, height);
      this.texture = managed ? null : GuiTexture.load(textureId);
      this.managedTextureId = managed ? textureId : null;
      this.smooth = smooth;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.u0 = u0;
      this.v0 = v0;
      this.u1 = u1;
      this.v1 = v1;
      this.color = color;
      this.cornerRadius = cornerRadius;
      float poseScale = Math.max((float)Math.hypot((double)pose.m00(), (double)pose.m01()), (float)Math.hypot((double)pose.m10(), (double)pose.m11()));
      this.deviceScale = (float)Math.max(1, MinecraftContext.mc.getWindow().getScaleFactor()) * Math.max(poseScale, 0.01F);
   }

   public TextureRenderState(Matrix3x2fc pose, Identifier textureId, float x, float y, float width, float height, int color, ScreenRect scissor) {
      this(pose, textureId, false, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, color, 0.0F, scissor);
   }

   public void setupVertices(VertexConsumer vertices) {
      float halfWidth = this.width * 0.5F;
      float halfHeight = this.height * 0.5F;
      int packedSizeX = UiVertexPacking.packSize(this.width);
      int packedSizeY = UiVertexPacking.packSize(this.height);
      this.addVertex(vertices, this.x, this.y, this.u0, this.v0, -halfWidth, -halfHeight, packedSizeX, packedSizeY);
      this.addVertex(vertices, this.x, this.y + this.height, this.u0, this.v1, -halfWidth, halfHeight, packedSizeX, packedSizeY);
      this.addVertex(vertices, this.x + this.width, this.y + this.height, this.u1, this.v1, halfWidth, halfHeight, packedSizeX, packedSizeY);
      this.addVertex(vertices, this.x + this.width, this.y, this.u1, this.v0, halfWidth, -halfHeight, packedSizeX, packedSizeY);
   }

   private void addVertex(VertexConsumer vertexConsumer, float x, float y, float u, float v, float localX, float localY, int packedSizeX, int packedSizeY) {
      vertexConsumer.vertex(this.transformX(x, y), this.transformY(x, y), 0.0F)
         .color(this.color)
         .texture(u, v)
         .overlay(UiVertexPacking.packSignedSize(localX), UiVertexPacking.packSignedSize(localY))
         .light(packedSizeX, packedSizeY)
         .normal(0.0F, 0.0F, 1.0F)
         .lineWidth(Math.max(this.cornerRadius, 0.0F));
   }

   public RenderPipeline pipeline() {
      return GuiPipelines.TEXTURE;
   }

   public TextureSetup textureSetup() {
      if (this.texture != null) {
         float uSpan = Math.max(Math.abs(this.u1 - this.u0), 0.01F);
         float vSpan = Math.max(Math.abs(this.v1 - this.v0), 0.01F);
         return this.texture.textureSetup(this.width * this.deviceScale / uSpan, this.height * this.deviceScale / vSpan);
      } else {
         return TextureSetup.of(
            MinecraftContext.mc.getTextureManager().getTexture(this.managedTextureId).getGlTextureView(),
            RenderSystem.getSamplerCache().get(this.smooth ? FilterMode.LINEAR : FilterMode.NEAREST, false)
         );
      }
   }
}

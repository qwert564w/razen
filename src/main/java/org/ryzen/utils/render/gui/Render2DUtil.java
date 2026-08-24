package org.ryzen.utils.render.gui;

import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.ryzen.context.MinecraftContext;
import org.ryzen.context.RenderContext;
import org.ryzen.mixin.accessor.GuiGraphicsExtractorAccessor;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Theme;

@Environment(EnvType.CLIENT)
public final class Render2DUtil {
   private static final Render2DUtil.FrameState FRAME_STATE = new Render2DUtil.FrameState();
   private static final Deque<ScreenRect> SCISSORS = new ArrayDeque<>();
   private static float backdropBlurScale = 1.0F;

   private Render2DUtil() {
   }

   public static void setBackdropBlurScale(float scale) {
      backdropBlurScale = Math.max(0.0F, Math.min(1.0F, scale));
   }

   public static Render2DUtil.RectBuilder rect(float x, float y, float width, float height) {
      return new Render2DUtil.RectBuilder(x, y, width, height);
   }

   public static Render2DUtil.TextBuilder text(float x, float y, float size, String text) {
      return new Render2DUtil.TextBuilder(x, y, size, text);
   }

   public static Render2DUtil.TextureBuilder texture(float x, float y, float width, float height, Identifier textureId) {
      return new Render2DUtil.TextureBuilder(x, y, width, height, textureId);
   }

   public static Render2DUtil.ColorGridBuilder colorGrid(float x, float y, float cellSize, int columns, int[] colors) {
      return new Render2DUtil.ColorGridBuilder(x, y, cellSize, columns, colors);
   }

   public static void menuBackground(float x, float y, float width, float height, float radius, float seconds, int mode, int primaryColor, int secondaryColor) {
      if (!(width <= 0.0F) && !(height <= 0.0F) && !hasEmptyScissor() && RenderContext.isIn2D()) {
         DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
         if (extractor != null) {
            Render2DUtil.FrameState frameState = FRAME_STATE;
            frameState.queued
               .add(
                  new MenuBackgroundRenderState(
                     extractor.getMatrices(), x, y, x + width, y + height, seconds, mode, radius, primaryColor, secondaryColor, currentScissor()
                  )
               );
            frameState.pendingCount = frameState.queued.size();
         }
      }
   }

   public static void beginFrame() {
      Render2DUtil.FrameState frameState = FRAME_STATE;
      frameState.queued.clear();
      frameState.pendingCount = 0;
      SCISSORS.clear();
   }

   public static void pushScissor(float x, float y, float width, float height) {
      int scissorX = Math.round(x);
      int scissorY = Math.round(y);
      int scissorWidth = Math.max(0, Math.round(x + width) - scissorX);
      int scissorHeight = Math.max(0, Math.round(y + height) - scissorY);
      ScreenRect next = new ScreenRect(scissorX, scissorY, scissorWidth, scissorHeight);
      if (!SCISSORS.isEmpty()) {
         next = SCISSORS.peek().intersection(next);
         if (next == null) {
            next = ScreenRect.empty();
         }
      }

      if (next.width() > 0 && next.height() > 0) {
         int left = Math.max(0, next.getLeft());
         int top = Math.max(0, next.getTop());
         int clampedWidth = next.getRight() - left;
         int clampedHeight = next.getBottom() - top;
         next = clampedWidth > 0 && clampedHeight > 0 ? new ScreenRect(left, top, clampedWidth, clampedHeight) : ScreenRect.empty();
      }

      if (next.width() > 0 && next.height() > 0) {
         Window window = MinecraftContext.mc.getWindow();
         ScreenRect screen = new ScreenRect(0, 0, window.getScaledWidth(), window.getScaledHeight());
         if (screen.intersection(next) == null) {
            next = ScreenRect.empty();
         }
      }

      SCISSORS.push(next);
   }

   public static void popScissor() {
      if (SCISSORS.isEmpty()) {
         throw new IllegalStateException("No active render scissor");
      } else {
         SCISSORS.pop();
      }
   }

   public static boolean isPointScissored(float x, float y) {
      if (SCISSORS.isEmpty()) {
         return false;
      } else {
         ScreenRect scissor = SCISSORS.peek();
         return x < (float)scissor.getLeft() || x >= (float)scissor.getRight() || y < (float)scissor.getTop() || y >= (float)scissor.getBottom();
      }
   }

   private static ScreenRect currentScissor() {
      return SCISSORS.peek();
   }

   private static boolean hasEmptyScissor() {
      ScreenRect scissor = currentScissor();
      return scissor != null && (scissor.width() <= 0 || scissor.height() <= 0);
   }

   public static int flush() {
      Render2DUtil.FrameState frameState = FRAME_STATE;
      DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
      if (extractor == null) {
         frameState.queued.clear();
         frameState.lastFlushedCount = 0;
         frameState.pendingCount = 0;
         return 0;
      } else {
         GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor)extractor;

         for (SimpleGuiElementRenderState renderState : frameState.queued) {
            accessor.getGuiRenderState().addSimpleElement(renderState);
         }

         int flushed = frameState.queued.size();
         frameState.queued.clear();
         frameState.pendingCount = 0;
         frameState.lastFlushedCount = flushed;
         return flushed;
      }
   }

   public static int pendingDrawCount() {
      return FRAME_STATE.pendingCount;
   }

   public static int lastFlushedDrawCount() {
      return FRAME_STATE.lastFlushedCount;
   }

   @Environment(EnvType.CLIENT)
   public static final class ColorGridBuilder {
      private final float x;
      private final float y;
      private final float cellSize;
      private final int columns;
      private final int[] colors;
      private int color = -1;
      private float radius;
      private int selectedIndex = -1;
      private float selectedThickness;
      private int hoveredIndex = -1;
      private float hoveredThickness;

      private ColorGridBuilder(float x, float y, float cellSize, int columns, int[] colors) {
         this.x = x;
         this.y = y;
         this.cellSize = cellSize;
         this.columns = columns;
         this.colors = Objects.requireNonNull(colors, "colors");
      }

      public Render2DUtil.ColorGridBuilder radius(float radius) {
         this.radius = Math.max(0.0F, radius);
         return this;
      }

      public Render2DUtil.ColorGridBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.ColorGridBuilder selected(int index, float thickness) {
         this.selectedIndex = index;
         this.selectedThickness = Math.max(0.0F, thickness);
         return this;
      }

      public Render2DUtil.ColorGridBuilder hovered(int index, float thickness) {
         this.hoveredIndex = index;
         this.hoveredThickness = Math.max(0.0F, thickness);
         return this;
      }

      public void draw() {
         if (!(this.cellSize <= 0.0F) && this.columns > 0 && this.colors.length != 0 && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.colorGrid().draw() can only be used inside a 2D render context");
            } else {
               DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
               if (extractor == null) {
                  throw new IllegalStateException("Render2DUtil.colorGrid().draw() requires an active GuiGraphics");
               } else {
                  Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
                  frameState.queued
                     .add(
                        new ColorGridRenderState(
                           extractor.getMatrices(),
                           this.x,
                           this.y,
                           this.cellSize,
                           this.columns,
                           this.radius,
                           this.color,
                           this.selectedIndex,
                           this.selectedThickness,
                           this.hoveredIndex,
                           this.hoveredThickness,
                           this.colors,
                           Render2DUtil.currentScissor()
                        )
                     );
                  frameState.pendingCount = frameState.queued.size();
               }
            }
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class FrameState {
      private final List<SimpleGuiElementRenderState> queued = new ArrayList<>(128);
      private int pendingCount;
      private int lastFlushedCount;
   }

   @Environment(EnvType.CLIENT)
   public static final class RectBuilder {
      public static final float BLUR_TINT_SCALE = 0.8F;
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private int topLeftColor = -1;
      private int topRightColor = -1;
      private int bottomRightColor = -1;
      private int bottomLeftColor = -1;
      private float topLeftRadius;
      private float topRightRadius;
      private float bottomRightRadius;
      private float bottomLeftRadius;
      private float borderThickness;
      private int borderColor = 0;
      private int shadowColor = 0;
      private float shadowBlur;
      private float backdropBlurRadius;
      private float backdropBlurOpacity = 1.0F;

      private RectBuilder(float x, float y, float width, float height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      public Render2DUtil.RectBuilder color(int color) {
         return this.color(color, color, color, color);
      }

      public Render2DUtil.RectBuilder color(int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor) {
         this.topLeftColor = topLeftColor;
         this.topRightColor = topRightColor;
         this.bottomRightColor = bottomRightColor;
         this.bottomLeftColor = bottomLeftColor;
         return this;
      }

      public Render2DUtil.RectBuilder radius(float radius) {
         return this.radius(radius, radius, radius, radius);
      }

      public Render2DUtil.RectBuilder radius(float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius) {
         this.topLeftRadius = Math.max(0.0F, topLeftRadius);
         this.topRightRadius = Math.max(0.0F, topRightRadius);
         this.bottomRightRadius = Math.max(0.0F, bottomRightRadius);
         this.bottomLeftRadius = Math.max(0.0F, bottomLeftRadius);
         return this;
      }

      public Render2DUtil.RectBuilder border(float thickness, int color) {
         this.borderThickness = Math.max(0.0F, thickness);
         this.borderColor = color;
         return this;
      }

      public Render2DUtil.RectBuilder shadow(int color, float blur) {
         this.shadowColor = color;
         this.shadowBlur = Math.max(0.0F, blur);
         return this;
      }

      public Render2DUtil.RectBuilder glass(float alpha, float borderThickness, float blurRadius) {
         return this.color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, alpha))
            .border(Math.max(0.5F, borderThickness), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
            .blur(blurRadius, alpha);
      }

      public Render2DUtil.RectBuilder blur(float radius) {
         return this.blur(radius, 1.0F);
      }

      public Render2DUtil.RectBuilder blur(float radius, float opacity) {
         this.backdropBlurRadius = Math.max(0.0F, radius);
         this.backdropBlurOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         return this;
      }

      public void draw() {
         if (!(this.width <= 0.0F) && !(this.height <= 0.0F) && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.draw() can only be used inside a 2D render context");
            } else {
               DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
               if (extractor == null) {
                  throw new IllegalStateException("Render2DUtil.draw() requires an active GuiGraphics");
               } else {
                  Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
                  this.queueShadow(frameState, extractor);
                  if (!this.queueBackdropBlur(frameState, extractor)) {
                     RectRenderState renderState = new RectRenderState(
                        extractor.getMatrices(),
                        this.x,
                        this.y,
                        this.x + this.width,
                        this.y + this.height,
                        this.topLeftColor,
                        this.bottomLeftColor,
                        this.bottomRightColor,
                        this.topRightColor,
                        this.topLeftRadius,
                        this.topRightRadius,
                        this.bottomRightRadius,
                        this.bottomLeftRadius,
                        this.borderThickness,
                        this.borderColor,
                        Render2DUtil.currentScissor()
                     );
                     frameState.queued.add(renderState);
                     frameState.pendingCount = frameState.queued.size();
                  }
               }
            }
         }
      }

      private boolean queueBackdropBlur(Render2DUtil.FrameState frameState, DrawContext extractor) {
         float effectiveBlurRadius = this.backdropBlurRadius * Render2DUtil.backdropBlurScale;
         if (!(effectiveBlurRadius <= 0.05F) && !(this.backdropBlurOpacity <= 0.01F)) {
            GpuTextureView backdropView = GuiBackdrop.acquireView();
            if (backdropView == null) {
               return false;
            } else {
               int guiScale = Math.max(1, MinecraftContext.mc.getWindow().getScaleFactor());
               int tintAlpha = Math.round((float)(this.topLeftColor >>> 24 & 0xFF) * 0.8F);
               frameState.queued
                  .add(
                     new BlurRectRenderState(
                        extractor.getMatrices(),
                        this.x,
                        this.y,
                        this.x + this.width,
                        this.y + this.height,
                        this.topLeftColor & 16777215 | tintAlpha << 24,
                        this.topLeftRadius,
                        effectiveBlurRadius * (float)guiScale,
                        this.backdropBlurOpacity,
                        backdropView,
                        Render2DUtil.currentScissor()
                     )
                  );
               if (this.borderThickness > 0.0F && this.borderColor >>> 24 != 0) {
                  frameState.queued
                     .add(
                        new RectRenderState(
                           extractor.getMatrices(),
                           this.x,
                           this.y,
                           this.x + this.width,
                           this.y + this.height,
                           0,
                           this.topLeftRadius,
                           this.borderThickness,
                           this.borderColor,
                           Render2DUtil.currentScissor()
                        )
                     );
               }

               frameState.pendingCount = frameState.queued.size();
               return true;
            }
         } else {
            return false;
         }
      }

      private void queueShadow(Render2DUtil.FrameState frameState, DrawContext extractor) {
         int alpha = this.shadowColor >>> 24 & 0xFF;
         if (alpha != 0 && !(this.shadowBlur <= 0.0F)) {
            boolean glass = this.backdropBlurRadius * Render2DUtil.backdropBlurScale > 0.05F && this.backdropBlurOpacity > 0.01F;
            RectRenderState shadowState = glass
               ? RectRenderState.glassShadow(
                  extractor.getMatrices(),
                  this.x,
                  this.y,
                  this.x + this.width,
                  this.y + this.height,
                  this.topLeftRadius,
                  this.topRightRadius,
                  this.bottomRightRadius,
                  this.bottomLeftRadius,
                  this.shadowBlur,
                  this.shadowColor,
                  Render2DUtil.currentScissor()
               )
               : RectRenderState.shadow(
                  extractor.getMatrices(),
                  this.x,
                  this.y,
                  this.x + this.width,
                  this.y + this.height,
                  this.topLeftRadius,
                  this.topRightRadius,
                  this.bottomRightRadius,
                  this.bottomLeftRadius,
                  this.shadowBlur,
                  this.shadowColor,
                  Render2DUtil.currentScissor()
               );
            frameState.queued.add(shadowState);
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class TextBuilder {
      private final float x;
      private final float y;
      private final float size;
      private final String text;
      private MsdfFont font;
      private MsdfFontFamily family;
      private int weight = -1;
      private UiFontStyle fontStyle = UiFontStyle.REGULAR;
      private int color = -1;
      private int outlineColor = 0;
      private float outlineThickness;
      private TextAlign align = TextAlign.LEFT;
      private float offsetX;
      private float offsetY;

      private TextBuilder(float x, float y, float size, String text) {
         this.x = x;
         this.y = y;
         this.size = size;
         this.text = Objects.requireNonNull(text, "text");
      }

      public Render2DUtil.TextBuilder font(Identifier fontId) {
         this.font = MsdfFont.load(Objects.requireNonNull(fontId, "fontId"));
         return this;
      }

      public Render2DUtil.TextBuilder font(MsdfFont font) {
         this.font = Objects.requireNonNull(font, "font");
         return this;
      }

      public Render2DUtil.TextBuilder family(MsdfFontFamily family) {
         this.family = Objects.requireNonNull(family, "family");
         return this;
      }

      public Render2DUtil.TextBuilder weight(int weight) {
         if (weight >= 1 && weight <= 1000) {
            this.weight = weight;
            return this;
         } else {
            throw new IllegalArgumentException("Font weight out of range: " + weight);
         }
      }

      public Render2DUtil.TextBuilder style(UiFontStyle fontStyle) {
         this.fontStyle = Objects.requireNonNull(fontStyle, "fontStyle");
         return this;
      }

      public Render2DUtil.TextBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.TextBuilder outline(int color, float thickness) {
         this.outlineColor = color;
         this.outlineThickness = thickness;
         return this;
      }

      public Render2DUtil.TextBuilder align(TextAlign align) {
         this.align = Objects.requireNonNull(align, "align");
         return this;
      }

      public Render2DUtil.TextBuilder offset(float offsetX, float offsetY) {
         this.offsetX = offsetX;
         this.offsetY = offsetY;
         return this;
      }

      public void draw() {
         if (!this.text.isEmpty() && !(this.size <= 0.0F) && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.text().draw() can only be used inside a 2D render context");
            } else {
               DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
               if (extractor == null) {
                  throw new IllegalStateException("Render2DUtil.text().draw() requires an active GuiGraphics");
               } else {
                  MsdfFont font = this.resolveFont();
                  MsdfFont.Paragraph paragraph = font.shape(this.text);
                  if (paragraph.missingGlyphs()) {
                     this.drawVanillaFallback(extractor);
                  } else {
                     float letterSpacing = this.size * this.fontStyle.letterSpacingEm();
                     TextRenderState renderState = new TextRenderState(
                        extractor.getMatrices(),
                        font,
                        this.x,
                        this.y,
                        this.size,
                        paragraph,
                        this.color,
                        this.outlineColor,
                        this.outlineThickness,
                        0.0F,
                        letterSpacing,
                        this.align,
                        this.offsetX,
                        this.offsetY,
                        Render2DUtil.currentScissor()
                     );
                     Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
                     frameState.queued.add(renderState);
                     frameState.pendingCount = frameState.queued.size();
                  }
               }
            }
         }
      }

      private MsdfFont resolveFont() {
         if (this.font != null) {
            return this.font;
         } else {
            MsdfFontFamily resolvedFamily = this.family != null ? this.family : UiFonts.sfPro();
            int resolvedWeight = this.weight > 0 ? this.weight : this.fontStyle.weight();
            return resolvedFamily.resolve(resolvedWeight);
         }
      }

      private void drawVanillaFallback(DrawContext extractor) {
         Render2DUtil.flush();
         TextRenderer vanillaFont = MinecraftContext.mc.textRenderer;
         float scale = this.size / 9.0F;
         int textWidth = vanillaFont.getWidth(this.text);

         int localX = switch (this.align) {
            case LEFT -> 0;
            case CENTER -> -textWidth / 2;
            case RIGHT -> -textWidth;
         };
         MsdfFont metricsFont = this.resolveFont();
         float baselineShift = metricsFont.ascender(this.size) - 7.0F * scale;
         extractor.getMatrices().pushMatrix();

         try {
            extractor.getMatrices().translate(this.x + this.offsetX, this.y + this.offsetY + baselineShift);
            extractor.getMatrices().scale(scale, scale);
            extractor.drawText(vanillaFont, this.text, localX, 0, this.color, false);
         } finally {
            extractor.getMatrices().popMatrix();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   public static final class TextureBuilder {
      private final float x;
      private final float y;
      private final float width;
      private final float height;
      private final Identifier textureId;
      private int color = -1;
      private boolean managed;
      private boolean smooth;
      private float u0;
      private float v0;
      private float u1 = 1.0F;
      private float v1 = 1.0F;
      private float radius;

      private TextureBuilder(float x, float y, float width, float height, Identifier textureId) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
         this.textureId = Objects.requireNonNull(textureId, "textureId");
      }

      public Render2DUtil.TextureBuilder color(int color) {
         this.color = color;
         return this;
      }

      public Render2DUtil.TextureBuilder managed() {
         this.managed = true;
         return this;
      }

      public Render2DUtil.TextureBuilder smooth() {
         this.smooth = true;
         return this;
      }

      public Render2DUtil.TextureBuilder uv(float u0, float v0, float u1, float v1) {
         this.u0 = u0;
         this.v0 = v0;
         this.u1 = u1;
         this.v1 = v1;
         return this;
      }

      public Render2DUtil.TextureBuilder radius(float radius) {
         this.radius = radius;
         return this;
      }

      public void draw() {
         if (!(this.width <= 0.0F) && !(this.height <= 0.0F) && !Render2DUtil.hasEmptyScissor()) {
            if (!RenderContext.isIn2D()) {
               throw new IllegalStateException("Render2DUtil.texture().draw() can only be used inside a 2D render context");
            } else {
               DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
               if (extractor == null) {
                  throw new IllegalStateException("Render2DUtil.texture().draw() requires an active GuiGraphics");
               } else {
                  TextureRenderState renderState = new TextureRenderState(
                     extractor.getMatrices(),
                     this.textureId,
                     this.managed,
                     this.x,
                     this.y,
                     this.width,
                     this.height,
                     this.u0,
                     this.v0,
                     this.u1,
                     this.v1,
                     this.color,
                     this.radius,
                     this.smooth,
                     Render2DUtil.currentScissor()
                  );
                  Render2DUtil.FrameState frameState = Render2DUtil.FRAME_STATE;
                  frameState.queued.add(renderState);
                  frameState.pendingCount = frameState.queued.size();
               }
            }
         }
      }
   }
}

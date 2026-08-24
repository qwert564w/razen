package org.ryzen.menu.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.ryzen.context.RenderContext;
import org.ryzen.feature.impl.visual.EmotionsFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.EmotionAnimator;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.MsdfFont;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.TextAlign;
import org.ryzen.utils.render.gui.UiFonts;

@Environment(EnvType.CLIENT)
public final class EmotionWheelScreen extends Screen {
   public static final int SLOT_NONE = -2;
   private static final float INNER_RADIUS = 28.0F;
   private static final float OUTER_RADIUS = 105.0F;
   private static final float LABEL_RADIUS = 72.0F;
   private static final float BACKDROP_RADIUS = 115.0F;
   private static final float LABEL_SIZE = 12.0F;
   private MsdfFont font;
   private int hoveredSlot = -2;

   public EmotionWheelScreen() {
      super(Text.literal("Emotions"));
   }

   public int getHoveredSlot() {
      return this.hoveredSlot;
   }

   protected void init() {
      this.font = UiFonts.sfProDisplay();
   }

   public boolean shouldPause() {
      return false;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      float centerX = (float)this.width / 2.0F;
      float centerY = (float)this.height / 2.0F;
      this.hoveredSlot = resolveSlot((float)mouseX - centerX, (float)mouseY - centerY);
      EmotionsFeature emotions = EmotionsFeature.getInstance();
      if (emotions != null) {
         emotions.updatePreview();
      }

      int accent = Theme.getAccent();
      RenderContext.enter2D(null, context, null);
      Render2DUtil.beginFrame();

      try {
         Render2DUtil.rect(centerX - 115.0F, centerY - 115.0F, 230.0F, 230.0F)
            .radius(115.0F)
            .color(-2013265920)
            .border(1.0F, ColorUtil.withAlpha(accent, 90))
            .draw();
         Render2DUtil.rect(centerX - 28.0F, centerY - 28.0F, 56.0F, 56.0F)
            .radius(28.0F)
            .color(-1441787888)
            .border(1.0F, this.hoveredSlot == -2 ? -43691 : ColorUtil.withAlpha(accent, 60))
            .draw();
         String[] labels = EmotionAnimator.EMOTIONS;

         for (int index = 0; index < labels.length; index++) {
            double angle = (Math.PI * 2) * (double)index / (double)labels.length - (Math.PI / 2);
            float x = centerX + (float)(Math.cos(angle) * 72.0);
            float y = centerY + (float)(Math.sin(angle) * 72.0);
            Render2DUtil.text(x, this.font.centeredTextY(y, 12.0F), 12.0F, labels[index])
               .font(this.font)
               .align(TextAlign.CENTER)
               .color(index == this.hoveredSlot ? accent : -1)
               .draw();
         }

         Render2DUtil.text(centerX, this.font.centeredTextY(centerY, 12.0F), 12.0F, "Отмена")
            .font(this.font)
            .align(TextAlign.CENTER)
            .color(this.hoveredSlot == -2 ? -43691 : -5592406)
            .draw();
         Render2DUtil.flush();
      } finally {
         RenderContext.exit2D();
      }
   }

   private static int resolveSlot(float deltaX, float deltaY) {
      double distance = Math.sqrt((double)(deltaX * deltaX + deltaY * deltaY));
      if (!(distance < 28.0) && !(distance > 105.0)) {
         double angle = Math.atan2((double)deltaY, (double)deltaX) + (Math.PI / 2);
         if (angle < 0.0) {
            angle += Math.PI * 2;
         }

         int count = EmotionAnimator.EMOTIONS.length;
         return MathHelper.floor(angle / (Math.PI * 2) * (double)count) % count;
      } else {
         return -2;
      }
   }
}

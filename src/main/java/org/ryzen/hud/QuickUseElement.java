package org.ryzen.hud;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.joml.Matrix3x2fStack;
import org.ryzen.context.RenderContext;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.impl.misc.ServerHelperFeature;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.gui.MsdfFont;
import org.ryzen.utils.render.gui.Render2DUtil;
import org.ryzen.utils.render.gui.TextAlign;
import org.ryzen.utils.render.gui.UiFontStyle;
import org.ryzen.utils.render.gui.UiFonts;

@Environment(EnvType.CLIENT)
public final class QuickUseElement extends HudElement {
   private static final float DESIGN_WIDTH = 178.691F;
   private static final float DESIGN_HEIGHT = 91.0F;
   private static final float ROW_STEP = 25.5F;
   private static final float FIRST_ROW_Y = 15.0F;
   private static final float SEPARATOR_OFFSET = 18.091F;
   private static final float LAST_ROW_BOTTOM = 25.0F;
   private static final float INNER_X = 4.964F;
   private static final float INNER_Y = 4.964F;
   private static final float INNER_WIDTH = 168.764F;
   private static final float INNER_HEIGHT = 81.073F;
   private static final float TEXT_X = 15.345F;
   private static final float ITEM_X = 119.345F;
   private static final float COUNT_RIGHT = 163.063F;
   private List<ServerHelperFeature.QuickUseEntry> entries = List.of();

   private static float rowY(int index) {
      return 15.0F + (float)index * 25.5F;
   }

   private static float panelHeight(int rows) {
      return rows <= 0 ? 0.0F : rowY(rows - 1) + 25.0F;
   }

   public QuickUseElement() {
      super("quick_use", "QuickUse");
   }

   @Override
   protected float defaultX(float unit) {
      return 98.0F * unit;
   }

   @Override
   protected float defaultY(float unit) {
      return 750.0F * unit;
   }

   @Override
   protected void layout(MinecraftClient mc, float unit) {
      ClientPlayerEntity player = mc.player;
      ServerHelperFeature helper = FeatureManager.INSTANCE.getEnabled(ServerHelperFeature.class);
      this.entries = helper != null && player != null ? helper.quickUseEntries(player) : List.of();
      if (this.entries.isEmpty() && showcase(mc)) {
         ItemStack snowballs = new ItemStack(Items.SNOWBALL, 14);
         this.entries = List.of(
            new ServerHelperFeature.QuickUseEntry(snowballs, 14, "CAPS"),
            new ServerHelperFeature.QuickUseEntry(snowballs, 14, "CAPS"),
            new ServerHelperFeature.QuickUseEntry(snowballs, 14, "CAPS")
         );
      }

      if (this.entries.isEmpty()) {
         this.width = 0.0F;
         this.height = 0.0F;
      } else {
         this.width = 178.691F * unit;
         this.height = panelHeight(this.entries.size()) * unit;
      }
   }

   @Override
   protected void draw(MinecraftClient mc, float unit) {
      if (!this.entries.isEmpty()) {
         float alpha = this.appearAlpha();
         this.drawPanel(13.0F * unit, unit, alpha);
         Render2DUtil.rect(this.x + 4.964F * unit, this.y + 4.964F * unit, 168.764F * unit, (panelHeight(this.entries.size()) - 9.928F) * unit)
            .color(ColorUtil.multiplyAlpha(HudPalette.SURFACE, alpha))
            .radius(13.0F * unit)
            .border(0.5F * unit, ColorUtil.multiplyAlpha(HudPalette.SURFACE_BORDER, alpha))
            .blur(50.0F * unit, alpha)
            .draw();
         MsdfFont font = UiFonts.sfProDisplay();
         float textSize = 10.0F * unit;

         for (int index = 0; index < this.entries.size(); index++) {
            ServerHelperFeature.QuickUseEntry entry = this.entries.get(index);
            float rowTop = this.y + rowY(index) * unit;
            String bind = compactBind(entry.bindLabel());
            Render2DUtil.text(this.x + 15.345F * unit, rowTop, textSize, bind).style(UiFontStyle.MEDIUM).color(ColorUtil.multiplyAlpha(-1, alpha)).draw();
            Render2DUtil.text(this.x + 163.063F * unit, rowTop, textSize, "x" + entry.count())
               .style(UiFontStyle.MEDIUM)
               .align(TextAlign.RIGHT)
               .color(ColorUtil.multiplyAlpha(-1, alpha))
               .draw();
            float markY = this.y + (rowY(index) + 2.655F) * unit;
            Render2DUtil.rect(this.x + 141.582F * unit, markY, 0.827F * unit, 7.445F * unit)
               .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, 27), alpha))
               .radius(2.0F * unit)
               .draw();
            if (index < this.entries.size() - 1) {
               float separatorY = this.y + (rowY(index) + 18.091F) * unit;
               Render2DUtil.rect(this.x + 14.891F * unit, separatorY, 148.082F * unit, 0.827F * unit)
                  .color(ColorUtil.multiplyAlpha(HudPalette.DIVIDER, alpha))
                  .radius(2.0F * unit)
                  .draw();
            }
         }

         if (!(alpha < 0.75F)) {
            DrawContext graphics = RenderContext.currentGuiGraphicsExtractor();
            if (graphics != null) {
               Render2DUtil.flush();
               Matrix3x2fStack pose = graphics.getMatrices();
               float guiScale = (float)mc.getWindow().getScaleFactor();

               for (int indexx = 0; indexx < this.entries.size(); indexx++) {
                  ItemStack stack = this.entries.get(indexx).icon();
                  if (!stack.isEmpty()) {
                     float itemSize = 11.0F * unit;
                     float itemX = this.x + 119.345F * unit;
                     float itemY = this.y + (rowY(indexx) + 1.0F) * unit;
                     itemX = (float)Math.round(itemX * guiScale) / guiScale;
                     itemY = (float)Math.round(itemY * guiScale) / guiScale;
                     pose.pushMatrix();
                     pose.translate(itemX, itemY);
                     pose.scale(itemSize / 16.0F);
                     graphics.drawItem(stack, 0, 0);
                     pose.popMatrix();
                  }
               }
            }
         }
      }
   }

   private static String compactBind(String label) {
      if (label != null && !label.isBlank()) {
         return switch (label) {
            case "Mouse Left" -> "M1";
            case "Mouse Right" -> "M2";
            case "Mouse Middle" -> "M3";
            default -> label.startsWith("Mouse ") ? "M" + label.substring(6) : label.toUpperCase();
         };
      } else {
         return "CAPS";
      }
   }
}

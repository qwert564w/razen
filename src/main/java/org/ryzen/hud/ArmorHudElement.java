package org.ryzen.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.ryzen.context.RenderContext;
import org.ryzen.menu.i18n.MenuText;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.Textures;
import org.ryzen.utils.render.Theme;
import org.ryzen.utils.render.gui.Render2DUtil;

@Environment(EnvType.CLIENT)
public final class ArmorHudElement extends HudElement {
   private static final float PADDING = 8.0F;
   private static final float CELL_SIZE = 27.5F;
   private static final float CELL_GAP = 2.5F;
   private static final float ITEM_SIZE = 20.0F;
   private static final float ITEM_TOP = 3.0F;
   private static final float BAR_WIDTH = 16.0F;
   private static final float BAR_HEIGHT = 2.5F;
   private static final float BAR_BOTTOM = 3.0F;
   private static final float CELL_RADIUS = 5.0F;
   private static final float CONTENT_RADIUS = 10.0F;
   private static final EquipmentSlot[] ARMOR_ORDER = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   public ArmorHudElement() {
      super("armor_hud", "ArmorHud");
   }

   @Override
   protected void layout(MinecraftClient mc, float unit) {
      float innerW = 16.0F * unit + 27.5F * (float)ARMOR_ORDER.length * unit + 2.5F * (float)(ARMOR_ORDER.length - 1) * unit;
      float innerH = 16.0F * unit + 27.5F * unit;
      this.width = 10.0F * unit + innerW;
      this.height = 38.0F * unit + 5.0F * unit + innerH;
   }

   @Override
   protected void draw(MinecraftClient mc, float unit) {
      ClientPlayerEntity player = mc.player;
      if (player != null) {
         float alpha = this.appearAlpha();
         float[] inner = this.drawCard(unit, alpha, 38.0F, Textures.Icons.SCAN_HEART, MenuText.ui("ArmorHud"));
         float innerX = inner[0];
         float innerY = inner[1];
         float innerW = inner[2];
         float innerH = inner[3];
         Render2DUtil.rect(innerX, innerY, innerW, innerH).color(ColorUtil.multiplyAlpha(ColorUtil.rgba(33, 33, 38, 102), alpha)).radius(10.0F * unit).draw();
         float cellSize = 27.5F * unit;
         float cellGap = 2.5F * unit;
         float cellsX = innerX + (innerW - (cellSize * (float)ARMOR_ORDER.length + cellGap * (float)(ARMOR_ORDER.length - 1))) / 2.0F;
         float cellsY = innerY + (innerH - cellSize) / 2.0F;

         for (int index = 0; index < ARMOR_ORDER.length; index++) {
            float cellX = cellsX + (float)index * (cellSize + cellGap);
            Render2DUtil.rect(cellX, cellsY, cellSize, cellSize).color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_SMALL, alpha)).radius(5.0F * unit).draw();
            ItemStack stack = player.getEquippedStack(ARMOR_ORDER[index]);
            drawDurability(stack, cellX, cellsY, cellSize, unit, alpha);
         }

         if (!(alpha < 0.75F)) {
            DrawContext extractor = RenderContext.currentGuiGraphicsExtractor();
            if (extractor != null) {
               Render2DUtil.flush();
               Matrix3x2fStack pose = extractor.getMatrices();
               float guiScale = (float)mc.getWindow().getScaleFactor();
               float itemSize = 20.0F * unit;
               float itemScale = itemSize / 16.0F;

               for (int index = 0; index < ARMOR_ORDER.length; index++) {
                  ItemStack stack = player.getEquippedStack(ARMOR_ORDER[index]);
                  if (!stack.isEmpty()) {
                     float cellX = cellsX + (float)index * (cellSize + cellGap);
                     float itemX = cellX + (cellSize - itemSize) / 2.0F;
                     float itemY = cellsY + 3.0F * unit;
                     itemX = (float)Math.round(itemX * guiScale) / guiScale;
                     itemY = (float)Math.round(itemY * guiScale) / guiScale;
                     pose.pushMatrix();
                     pose.translate(itemX, itemY);
                     pose.scale(itemScale);
                     extractor.drawItem(stack, 0, 0);
                     pose.popMatrix();
                  }
               }
            }
         }
      }
   }

   private static void drawDurability(ItemStack stack, float cellX, float cellY, float cellSize, float unit, float alpha) {
      if (!stack.isEmpty() && stack.isDamageable()) {
         float remaining = Math.clamp((float)(stack.getMaxDamage() - stack.getDamage()) / (float)Math.max(1, stack.getMaxDamage()), 0.0F, 1.0F);
         float barWidth = 16.0F * unit;
         float barHeight = 2.5F * unit;
         float barX = cellX + (cellSize - barWidth) / 2.0F;
         float barY = cellY + cellSize - 3.0F * unit - barHeight;
         Render2DUtil.rect(barX, barY, barWidth, barHeight).color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha)).radius(barHeight).draw();
         float fillWidth = barWidth * remaining;
         if (!(fillWidth <= 0.0F)) {
            int color = remaining > 0.5F ? Theme.Colors.TRAFFIC_MAXIMIZE : (remaining > 0.25F ? Theme.Colors.TRAFFIC_MINIMIZE : Theme.Colors.TRAFFIC_CLOSE);
            Render2DUtil.rect(barX, barY, Math.max(barHeight, fillWidth), barHeight).color(ColorUtil.multiplyAlpha(color, alpha)).radius(barHeight).draw();
         }
      }
   }
}

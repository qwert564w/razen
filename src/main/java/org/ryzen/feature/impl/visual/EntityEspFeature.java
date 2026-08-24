package org.ryzen.feature.impl.visual;

import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.render.Render2DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.ColorMode;
import org.ryzen.feature.setting.ColorSetting;
import org.ryzen.feature.setting.ModeSetting;
import org.ryzen.feature.setting.MultiSelectSetting;
import org.ryzen.feature.setting.NumberSetting;
import org.ryzen.utils.ColorUtil;
import org.ryzen.utils.render.HurtUtil;
import org.ryzen.utils.render.Render3DUtil;
import org.ryzen.utils.render.gui.Render2DUtil;

@Environment(EnvType.CLIENT)
public final class EntityEspFeature extends Feature {
   private static final int BASE_COLOR = -14188801;
   private static final float ESP_ALPHA = 0.92F;
   private static final float FADE_START_DISTANCE = 0.4F;
   private static final float FADE_FULL_DISTANCE = 2.0F;
   private static final float MIN_RENDER_ALPHA = 0.05F;
   private static final float BOX_LINE_WIDTH = 1.0F;
   private final MultiSelectSetting targets = this.register(
      new MultiSelectSetting("Targets", Set.of("Players", "Hostile"), "Players", "Hostile", "Passive", "Items", "Projectiles")
   );
   private final ModeSetting boxMode = this.register(new ModeSetting("Box Mode", "Corners", "Corners", "Box"));
   private final BooleanSetting rounded = this.register(new BooleanSetting("Rounded", false));
   private final BooleanSetting healthBar = this.register(new BooleanSetting("Health Bar", true));
   private final BooleanSetting box = this.register(new BooleanSetting("Box", true));
   private final NumberSetting distance = this.register(new NumberSetting("Distance", 64.0, 5.0, 128.0, 1.0, " blocks"));
   private final ModeSetting colorMode = this.register(ColorMode.setting());
   private final ColorSetting espColor = this.register(
      new ColorSetting("Color", -14188801).configKey("Glow Color").visibleWhen(() -> ColorMode.isCustom(this.colorMode))
   );

   public EntityEspFeature() {
      super("EntityESP", "Draws boxes and glow around selected entities.", FeatureCategory.VISUAL, 71);
   }

   @EventTarget
   public void onRender2D(Render2DEvent event) {
      MinecraftClient mc = event.getClient();
      if (mc != null && mc.world != null && mc.player != null) {
         float tickDelta = event.getDeltaTracker().getTickProgress(false);

         for (Entity entity : mc.world.getEntities()) {
            if (this.isValidOverlayTarget(mc, entity)) {
               float alpha = this.overlayAlpha(mc.player, entity);
               if (!(alpha < 0.05F)) {
                  Render3DUtil.ScreenBounds bounds = this.projectEntityBounds(mc, entity, tickDelta);
                  if (bounds != null && !(bounds.width() < 2.0F) && !(bounds.height() < 2.0F)) {
                     int color = HurtUtil.blend(this.resolveColor(), entity, alpha);
                     if (this.box.getValue()) {
                        if (this.boxMode.is("Corners")) {
                           if (this.rounded.getValue()) {
                              this.drawRoundedCornerBox(bounds, color);
                           } else {
                              this.drawCornerBox(bounds, color);
                           }
                        } else if (this.rounded.getValue()) {
                           this.drawRoundedBox(bounds, color);
                        } else {
                           this.drawBox(bounds, color);
                        }
                     }

                     if (this.healthBar.getValue() && entity instanceof LivingEntity livingEntity) {
                        this.drawHealthBar(bounds, livingEntity, alpha);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isValidOverlayTarget(MinecraftClient mc, Entity entity) {
      if (entity == null || entity.isRemoved() || !entity.isAlive()) {
         return false;
      } else if (entity == mc.player && mc.options.getPerspective().isFirstPerson()) {
         return false;
      } else {
         return !this.isWithinRenderDistance(mc.player, entity) ? false : this.isTarget(entity);
      }
   }

   private boolean isWithinRenderDistance(PlayerEntity viewer, Entity entity) {
      if (viewer == null) {
         return false;
      } else {
         double maxDistance = this.distance.getValue();
         return viewer.squaredDistanceTo(entity) <= maxDistance * maxDistance;
      }
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return this.targets.isSelected("Players");
      } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
         return this.targets.isSelected("Items");
      } else if (!(entity instanceof ProjectileEntity) && !(entity instanceof EyeOfEnderEntity) && !(entity instanceof EvokerFangsEntity)) {
         SpawnGroup category = entity.getType().getSpawnGroup();
         if (category == SpawnGroup.MONSTER) {
            return this.targets.isSelected("Hostile");
         } else {
            return category != SpawnGroup.CREATURE
                  && category != SpawnGroup.AXOLOTLS
                  && category != SpawnGroup.AMBIENT
                  && category != SpawnGroup.UNDERGROUND_WATER_CREATURE
                  && category != SpawnGroup.WATER_CREATURE
                  && category != SpawnGroup.WATER_AMBIENT
               ? false
               : this.targets.isSelected("Passive");
         }
      } else {
         return this.targets.isSelected("Projectiles");
      }
   }

   private float overlayAlpha(PlayerEntity viewer, Entity entity) {
      float distance = viewer.distanceTo(entity);
      float progress = (distance - 0.4F) / 1.6F;
      return 0.92F * MathHelper.clamp(progress, 0.0F, 1.0F);
   }

   private int resolveColor() {
      return ColorMode.resolve(this.colorMode, this.espColor);
   }

   private Render3DUtil.ScreenBounds projectEntityBounds(MinecraftClient mc, Entity entity, float tickDelta) {
      Vec3d renderPosition = Render3DUtil.interpolatedPosition(entity, tickDelta);
      double halfWidth = (double)entity.getWidth() / 1.5;
      double height = (double)entity.getHeight() + 0.1 - (entity.isSneaking() ? 0.2 : 0.0);
      return Render3DUtil.projectBoxBounds(
         mc,
         renderPosition.x - halfWidth,
         renderPosition.y,
         renderPosition.z - halfWidth,
         renderPosition.x + halfWidth,
         renderPosition.y + height,
         renderPosition.z + halfWidth
      );
   }

   private void drawBox(Render3DUtil.ScreenBounds bounds, int color) {
      int minX = Math.round(bounds.minX());
      int minY = Math.round(bounds.minY());
      int maxX = Math.round(bounds.maxX());
      int maxY = Math.round(bounds.maxY());
      int width = maxX - minX;
      int height = maxY - minY;
      if (width > 0 && height > 0) {
         Render2DUtil.rect((float)minX, (float)minY, (float)width, (float)Math.max(1, Math.round(1.0F))).color(color).draw();
         Render2DUtil.rect((float)minX, (float)(maxY - 1), (float)width, 1.0F).color(color).draw();
         Render2DUtil.rect((float)minX, (float)minY, 1.0F, (float)height).color(color).draw();
         Render2DUtil.rect((float)(maxX - 1), (float)minY, 1.0F, (float)height).color(color).draw();
      }
   }

   private void drawCornerBox(Render3DUtil.ScreenBounds bounds, int color) {
      int minX = Math.round(bounds.minX());
      int minY = Math.round(bounds.minY());
      int maxX = Math.round(bounds.maxX());
      int maxY = Math.round(bounds.maxY());
      int width = maxX - minX;
      int height = maxY - minY;
      if (width > 0 && height > 0) {
         int cornerWidth = Math.max(2, Math.round((float)width / 4.0F));
         int cornerHeight = Math.max(2, Math.round((float)height / 4.0F));
         Render2DUtil.rect((float)minX, (float)minY, (float)cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect((float)minX, (float)minY, 1.0F, (float)cornerHeight).color(color).draw();
         Render2DUtil.rect((float)(maxX - cornerWidth), (float)minY, (float)cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect((float)(maxX - 1), (float)minY, 1.0F, (float)cornerHeight).color(color).draw();
         Render2DUtil.rect((float)minX, (float)(maxY - 1), (float)cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect((float)minX, (float)(maxY - cornerHeight), 1.0F, (float)cornerHeight).color(color).draw();
         Render2DUtil.rect((float)(maxX - cornerWidth), (float)(maxY - 1), (float)cornerWidth, 1.0F).color(color).draw();
         Render2DUtil.rect((float)(maxX - 1), (float)(maxY - cornerHeight), 1.0F, (float)cornerHeight).color(color).draw();
      }
   }

   private void drawRoundedBox(Render3DUtil.ScreenBounds bounds, int color) {
      float minX = (float)Math.round(bounds.minX());
      float minY = (float)Math.round(bounds.minY());
      float width = (float)Math.round(bounds.maxX()) - minX;
      float height = (float)Math.round(bounds.maxY()) - minY;
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float radius = Math.min(width, height) * 0.75F / 4.0F;
         Render2DUtil.rect(minX, minY, width, height).color(0).radius(radius).border(1.0F, color).draw();
      }
   }

   private void drawRoundedCornerBox(Render3DUtil.ScreenBounds bounds, int color) {
      float minX = (float)Math.round(bounds.minX());
      float minY = (float)Math.round(bounds.minY());
      float width = (float)Math.round(bounds.maxX()) - minX;
      float height = (float)Math.round(bounds.maxY()) - minY;
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         float cornerWidth = Math.max(3.0F, width / 4.0F);
         float cornerHeight = Math.max(3.0F, height / 4.0F);
         float radius = Math.min(cornerWidth, cornerHeight) * 0.75F;
         float maxY = minY + height;
         float maxX = minX + width;
         float[][] corners = new float[][]{{minX, minY}, {maxX - cornerWidth, minY}, {minX, maxY - cornerHeight}, {maxX - cornerWidth, maxY - cornerHeight}};

         for (float[] corner : corners) {
            Render2DUtil.pushScissor(corner[0], corner[1], cornerWidth, cornerHeight);
            Render2DUtil.rect(minX, minY, width, height).color(0).radius(radius).border(1.0F, color).draw();
            Render2DUtil.popScissor();
         }
      }
   }

   private void drawHealthBar(Render3DUtil.ScreenBounds bounds, LivingEntity entity, float alpha) {
      float health = MathHelper.clamp(entity.getHealth(), 0.0F, entity.getMaxHealth());
      float ratio = entity.getMaxHealth() <= 0.0F ? 0.0F : health / entity.getMaxHealth();
      int barHeight = Math.max(1, Math.round(bounds.height()));
      int filledHeight = Math.max(0, Math.round((float)barHeight * ratio));
      int x = Math.round(bounds.minX()) - 4;
      int y = Math.round(bounds.minY());
      Render2DUtil.rect((float)x, (float)y, 2.0F, (float)barHeight).color(ColorUtil.multiplyAlpha(Integer.MIN_VALUE, alpha)).draw();
      if (filledHeight > 0) {
         int fillY = y + (barHeight - filledHeight);
         int fillColor = ColorUtil.multiplyAlpha(ColorUtil.lerp(-65536, -16711936, ratio), alpha);
         Render2DUtil.rect((float)x, (float)fillY, 2.0F, (float)filledHeight).color(fillColor).draw();
      }
   }
}

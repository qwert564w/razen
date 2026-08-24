package org.ryzen.feature.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.Gizmo;
import net.minecraft.world.debug.gizmo.GizmoDrawer;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.GizmoDrawing.CollectorScope;
import org.ryzen.context.MinecraftContext;
import org.ryzen.event.EventTarget;
import org.ryzen.event.events.render.Render3DEvent;
import org.ryzen.feature.Feature;
import org.ryzen.feature.FeatureCategory;
import org.ryzen.feature.FeatureManager;
import org.ryzen.feature.setting.BooleanSetting;
import org.ryzen.feature.setting.NumberSetting;

@Environment(EnvType.CLIENT)
public final class HitBoxesFeature extends Feature implements MinecraftContext {
   private static final double MAX_RENDER_DISTANCE_SQR = 4096.0;
   private static final int HITBOX_COLOR = -16711809;
   private static final int EYE_COLOR = -43691;
   private static final float LINE_WIDTH = 1.5F;
   public final NumberSetting size = this.register(new NumberSetting("Size", 3.0, 1.0, 10.0, 0.5, ""));
   public final BooleanSetting withAura = this.register(new BooleanSetting("With Aura", false));
   public final BooleanSetting showSize = this.register(new BooleanSetting("Show Size", false));
   public final BooleanSetting yModification = this.register(new BooleanSetting("Y Size", false));

   public HitBoxesFeature() {
      super("HitBoxes", "Expands the collision size of surrounding entities for easier hits", FeatureCategory.COMBAT, 0);
   }

   public static HitBoxesFeature getInstance() {
      return FeatureManager.INSTANCE.getFeature(HitBoxesFeature.class);
   }

   public static HitBoxesFeature getEnabled() {
      return FeatureManager.INSTANCE.getEnabled(HitBoxesFeature.class);
   }

   public double getHorizontalExpansion() {
      return this.size.getValue() / 10.0;
   }

   public double getVerticalExpansion() {
      return this.yModification.getValue() ? this.getHorizontalExpansion() : 0.0;
   }

   public double getAuraExpansion() {
      return this.isEnabled() && this.withAura.getValue() ? this.getHorizontalExpansion() : 0.0;
   }

   public Box expandBoundingBox(Box boundingBox) {
      double horizontal = this.getHorizontalExpansion();
      return boundingBox.expand(horizontal, this.getVerticalExpansion(), horizontal);
   }

   public boolean appliesTo(Entity entity) {
      if (this.isEnabled() && entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
         return true;
      }

      return false;
   }

   @EventTarget
   public void onRender3D(Render3DEvent event) {
      if (this.isEnabled() && this.showSize.getValue() && mc.player != null && mc.world != null && event.getClient().worldRenderer != null) {
         float partialTick = event.getDeltaTracker().getTickProgress(false);
         CollectorScope ignored = event.getClient().worldRenderer.startDrawingGizmos();

         try {
            for (Entity entity : mc.world.getEntities()) {
               if (entity instanceof LivingEntity) {
                  LivingEntity livingEntity = (LivingEntity)entity;
                  if (livingEntity.isAlive() && entity != mc.player && !(mc.player.squaredDistanceTo(entity) > 4096.0)) {
                     Box box = this.interpolatedBox(livingEntity, partialTick);
                     GizmoDrawing.collect(new HitBoxesFeature.BoundingBoxGizmo(box, -16711809, 1.5F)).ignoreOcclusion();
                     double eyeY = MathHelper.lerp((double)partialTick, livingEntity.lastRenderY, livingEntity.getY())
                        + (double)livingEntity.getStandingEyeHeight();
                     Box eyeBox = new Box(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ);
                     GizmoDrawing.collect(new HitBoxesFeature.BoundingBoxGizmo(eyeBox, -43691, 1.5F)).ignoreOcclusion();
                  }
               }
            }
         } catch (Throwable var12) {
            if (ignored != null) {
               try {
                  ignored.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }
            }

            throw var12;
         }

         if (ignored != null) {
            ignored.close();
         }
      }
   }

   private Box interpolatedBox(LivingEntity entity, float partialTick) {
      Box box = this.expandBoundingBox(entity.getBoundingBox());
      double renderX = MathHelper.lerp((double)partialTick, entity.lastRenderX, entity.getX());
      double renderY = MathHelper.lerp((double)partialTick, entity.lastRenderY, entity.getY());
      double renderZ = MathHelper.lerp((double)partialTick, entity.lastRenderZ, entity.getZ());
      return box.offset(renderX - entity.getX(), renderY - entity.getY(), renderZ - entity.getZ());
   }

   @Environment(EnvType.CLIENT)
   private static record BoundingBoxGizmo(Box box, int color, float lineWidth) implements Gizmo {
      public void draw(GizmoDrawer consumer, float opacity) {
         Vec3d minMinMin = new Vec3d(this.box.minX, this.box.minY, this.box.minZ);
         Vec3d minMinMax = new Vec3d(this.box.minX, this.box.minY, this.box.maxZ);
         Vec3d minMaxMin = new Vec3d(this.box.minX, this.box.maxY, this.box.minZ);
         Vec3d minMaxMax = new Vec3d(this.box.minX, this.box.maxY, this.box.maxZ);
         Vec3d maxMinMin = new Vec3d(this.box.maxX, this.box.minY, this.box.minZ);
         Vec3d maxMinMax = new Vec3d(this.box.maxX, this.box.minY, this.box.maxZ);
         Vec3d maxMaxMin = new Vec3d(this.box.maxX, this.box.maxY, this.box.minZ);
         Vec3d maxMaxMax = new Vec3d(this.box.maxX, this.box.maxY, this.box.maxZ);
         consumer.addLine(minMinMin, minMinMax, this.color, this.lineWidth);
         consumer.addLine(minMinMin, minMaxMin, this.color, this.lineWidth);
         consumer.addLine(minMinMin, maxMinMin, this.color, this.lineWidth);
         consumer.addLine(minMinMax, minMaxMax, this.color, this.lineWidth);
         consumer.addLine(minMinMax, maxMinMax, this.color, this.lineWidth);
         consumer.addLine(minMaxMin, minMaxMax, this.color, this.lineWidth);
         consumer.addLine(minMaxMin, maxMaxMin, this.color, this.lineWidth);
         consumer.addLine(maxMinMin, maxMinMax, this.color, this.lineWidth);
         consumer.addLine(maxMinMin, maxMaxMin, this.color, this.lineWidth);
         consumer.addLine(maxMinMax, maxMaxMax, this.color, this.lineWidth);
         consumer.addLine(minMaxMax, maxMaxMax, this.color, this.lineWidth);
         consumer.addLine(maxMaxMin, maxMaxMax, this.color, this.lineWidth);
      }
   }
}

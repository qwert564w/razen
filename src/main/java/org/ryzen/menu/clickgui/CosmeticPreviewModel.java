package org.ryzen.menu.clickgui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.ryzen.context.MinecraftContext;
import org.ryzen.utils.math.MathUtil;

@Environment(EnvType.CLIENT)
public final class CosmeticPreviewModel {
   private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
   private static final float DEFAULT_ZOOM = 1.0F;
   private static final float MIN_ZOOM = 0.55F;
   private static final float MAX_ZOOM = 2.4F;
   private static final float MAX_PITCH = 45.0F;
   private static final float DRAG_DEGREES_PER_PIXEL = 0.85F;
   private static final float IDLE_SPIN = 11.0F;
   private static final float SETTLE_PER_SECOND = 9.0F;
   private static final float Y_OFFSET = 0.0625F;
   private static final float BOX_SCALE = 0.42F;
   private float yaw;
   private float pitch;
   private float zoom = 1.0F;
   private float shownYaw;
   private float shownPitch;
   private float shownZoom = 1.0F;
   private boolean dragging;
   private boolean touched;
   private double lastMouseX;
   private double lastMouseY;

   public void beginDrag(double mouseX, double mouseY) {
      this.dragging = true;
      this.touched = true;
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
   }

   public void drag(double mouseX, double mouseY) {
      if (this.dragging) {
         this.yaw = this.yaw + (float)(mouseX - this.lastMouseX) * 0.85F;
         this.pitch = MathUtil.clamp(this.pitch + (float)(mouseY - this.lastMouseY) * 0.85F, -45.0F, 45.0F);
         this.lastMouseX = mouseX;
         this.lastMouseY = mouseY;
      }
   }

   public void release() {
      this.dragging = false;
   }

   public void zoom(double amount) {
      this.touched = true;
      this.zoom = MathUtil.clamp(this.zoom + (float)amount * 0.12F, 0.55F, 2.4F);
   }

   public void reset() {
      this.yaw = 0.0F;
      this.pitch = 0.0F;
      this.zoom = 1.0F;
      this.touched = false;
   }

   public boolean isTouched() {
      return this.touched;
   }

   public void tick(float deltaSeconds) {
      if (!this.touched && !this.dragging) {
         this.yaw += 11.0F * deltaSeconds;
      }

      float settle = 1.0F - (float)Math.exp((double)(-9.0F * deltaSeconds));
      this.shownYaw = this.shownYaw + (this.yaw - this.shownYaw) * settle;
      this.shownPitch = this.shownPitch + (this.pitch - this.shownPitch) * settle;
      this.shownZoom = this.shownZoom + (this.zoom - this.shownZoom) * settle;
   }

   public boolean render(DrawContext graphics, LivingEntity entity, int left, int top, int right, int bottom) {
      if (graphics != null && entity != null && right - left >= 8 && bottom - top >= 8) {
         try {
            EntityRenderState state = extractRenderState(entity);
            if (state == null) {
               return false;
            } else {
               if (state instanceof LivingEntityRenderState living) {
                  living.bodyYaw = 180.0F + this.shownYaw;
                  living.relativeHeadYaw = 0.0F;
                  living.pitch = living.pose == EntityPose.GLIDING ? 0.0F : -this.shownPitch;
                  living.limbSwingAmplitude = 0.0F;
                  if (living.baseScale != 0.0F) {
                     living.width = living.width / living.baseScale;
                     living.height = living.height / living.baseScale;
                     living.baseScale = 1.0F;
                  }
               }

               int scale = Math.max(8, Math.round((float)(bottom - top) * 0.42F * this.shownZoom));
               Vector3f translation = new Vector3f(0.0F, state.height / 2.0F + 0.0625F, 0.0F);
               Quaternionf tilt = new Quaternionf().rotateX(this.shownPitch * (float) (Math.PI / 180.0));
               Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
               graphics.addEntity(state, (float)scale, translation, rotation, tilt, left, top, right, bottom);
               return true;
            }
         } catch (Exception var12) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static EntityRenderState extractRenderState(LivingEntity entity) {
      EntityRenderer renderer = MinecraftContext.mc.getEntityRenderDispatcher().getRenderer(entity);
      if (renderer == null) {
         return null;
      } else {
         EntityRenderState state = renderer.getAndUpdateRenderState(entity, 1.0F);
         state.light = 15728880;
         state.shadowPieces.clear();
         state.outlineColor = 0;
         state.displayName = null;
         return state;
      }
   }
}
